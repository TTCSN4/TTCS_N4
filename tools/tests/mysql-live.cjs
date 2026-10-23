'use strict';

// Opt-in real MySQL acceptance suite. Creates and drops only its own ttcs_qa_* databases.
// Run with DB_PASSWORD in the process environment: npm run test:mysql
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const net = require('node:net');
const { spawn } = require('node:child_process');
const { randomBytes, createHash } = require('node:crypto');
const mysql = require('mysql2/promise');
const root = path.resolve(__dirname, '../..');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
// MySQL JSON normalizes object key order; compare content rather than serialization order.
const canonical = value => Array.isArray(value) ? value.map(canonical)
  : value && typeof value === 'object' ? Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])])) : value;
const digest = value => createHash('sha256').update(JSON.stringify(canonical(value))).digest('hex');
const documentOf = row => typeof row.document === 'string' ? JSON.parse(row.document) : row.document;

// Never accept DB_NAME as the DDL/DML target: a caller may have production settings loaded.
test('real MySQL schema, HTTP business flows, transactions, concurrency and recovery', { timeout: 180000 }, async t => {
  assert(process.env.DB_PASSWORD, 'Set DB_PASSWORD in the process environment before running test:mysql.');
  const config = {
    host: process.env.DB_HOST || '127.0.0.1', port: Number(process.env.DB_PORT || 3306),
    user: process.env.DB_USERNAME || 'root', password: process.env.DB_PASSWORD,
    charset: 'utf8mb4', timezone: '+07:00', dateStrings: true
  };
  const prefix = 'ttcs_qa_' + Date.now() + '_' + randomBytes(4).toString('hex');
  const names = { main: prefix, legacy: prefix + '_legacy', recovery: prefix + '_recovery', snapshot: prefix + '_snapshot' };
  const created = new Set(), servers = new Set();
  const passwords = Object.fromEntries(['admin', 'pm', 'member'].map(role => [role, randomBytes(18).toString('hex')]));
  const report = { startedAt: new Date().toISOString(), scope: 'Node web runtime with a real MySQL database', cases: [], cleanup: [], passed: false };
  const schema = fs.readFileSync(path.join(root, 'database/web-schema.sql'), 'utf8');
  const migration = fs.readFileSync(path.join(root, 'database/migrations/20261023_web_participant_groups.sql'), 'utf8');
  let control, db, primary, adminCookie, memberCookie, pmCookie, meeting, group, createdUser;

  function identifier(name) {
    assert(Object.values(names).includes(name) && /^ttcs_qa_\d+_[a-f0-9]{8}(?:_legacy|_recovery|_snapshot)?$/.test(name), 'Unsafe database identifier');
    return '`' + name + '`';
  }
  async function createDatabase(name) {
    await control.query('CREATE DATABASE ' + identifier(name) + ' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci');
    created.add(name);
    return mysql.createConnection({ ...config, database: name, multipleStatements: true });
  }
  async function freePort() {
    const probe = net.createServer();
    await new Promise((resolve, reject) => { probe.once('error', reject); probe.listen(0, '127.0.0.1', resolve); });
    const port = probe.address().port;
    await new Promise(resolve => probe.close(resolve));
    return port;
  }
  async function stop(server) {
    if (!server || server.child.exitCode !== null || server.child.signalCode !== null) return;
    const exited = new Promise(resolve => server.child.once('exit', resolve));
    server.child.kill();
    const timeout = setTimeout(() => server.child.kill('SIGKILL'), 2500);
    await exited; clearTimeout(timeout);
  }
  async function start(database = names.main, expectFailure = false) {
    const port = await freePort();
    const env = { ...process.env, DB_HOST: config.host, DB_PORT: String(config.port), DB_USERNAME: config.user,
      DB_PASSWORD: config.password, DB_NAME: database, DATA_STORE: 'mysql', APP_MODE: 'local', DEMO_MODE: '1', PORT: String(port),
      INITIAL_ADMIN_PASSWORD: passwords.admin, INITIAL_PM_PASSWORD: passwords.pm, INITIAL_MEMBER_PASSWORD: passwords.member };
    const child = spawn(process.execPath, [path.join(root, 'server.cjs')], { cwd: root, env, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
    const server = { child, base: 'http://127.0.0.1:' + port, output: '' };
    servers.add(server);
    child.stdout.on('data', data => { server.output += data; });
    child.stderr.on('data', data => { server.output += data; });
    for (let attempt = 0; attempt < 150; attempt++) {
      if (child.exitCode !== null || child.signalCode !== null) {
        if (expectFailure) { assert.notEqual(child.exitCode, 0); return server; }
        throw Error('MySQL web startup failed: ' + server.output.slice(-1500));
      }
      if (!expectFailure) {
        try { if ((await fetch(server.base + '/__portal/health', { signal: AbortSignal.timeout(1000) })).ok) return server; } catch {}
      }
      await sleep(50);
    }
    await stop(server);
    throw Error('MySQL web startup timed out: ' + server.output.slice(-1500));
  }
  async function request(server, url, cookie, method = 'GET', body, expected = 200) {
    const response = await fetch(server.base + url, { method, signal: AbortSignal.timeout(15000),
      headers: { ...(cookie ? { cookie } : {}), ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
      body: body === undefined ? undefined : JSON.stringify(body) });
    const data = await response.json();
    if (expected !== null) assert.equal(response.status, expected, method + ' ' + url + ': ' + JSON.stringify(data));
    return { status: response.status, data, headers: response.headers };
  }
  async function login(server, username, password = passwords[username]) {
    const result = await request(server, '/api/auth/login', null, 'POST', { username, password });
    const cookie = result.headers.get('set-cookie');
    assert(cookie && /HttpOnly/.test(cookie));
    return cookie.split(';')[0];
  }
  const api = (url, method = 'GET', body, expected = 200, cookie = adminCookie) => request(primary, url, cookie, method, body, expected);
  const payload = (overrides = {}) => ({ title: 'MySQL acceptance', organizerId: 'DEMO01', roomId: 'A101', participants: ['DEMO01'],
    startTime: '2040-02-10T13:00:00+07:00', endTime: '2040-02-10T14:00:00+07:00', equipment: [], ...overrides });
  async function stored(connection = db) {
    const [rows] = await connection.query('SELECT revision, document FROM web_state WHERE id=1');
    return { revision: Number(rows[0].revision), document: documentOf(rows[0]) };
  }
  async function unchanged(fn) {
    const before = digest(await stored());
    await fn();
    assert.equal(digest(await stored()), before, 'Rejected/read-only request changed the persisted snapshot');
  }
  async function check(name, fn) {
    let failure;
    await t.test(name, async () => {
      const startTime = Date.now();
      try { await fn(); report.cases.push({ name, passed: true, durationMs: Date.now() - startTime }); }
      catch (error) { failure = error; report.cases.push({ name, passed: false, durationMs: Date.now() - startTime, error: error.message }); throw error; }
    });
    if (failure) throw Error('Stopped after failed MySQL acceptance case: ' + name);
  }

  try {
    control = await mysql.createConnection(config);
    const [version] = await control.query('SELECT VERSION() AS version, @@version_comment AS distribution, @@character_set_server AS charset, @@transaction_isolation AS isolationLevel');
    report.server = version[0];
    db = await createDatabase(names.main);

    await check('fresh schema creates six InnoDB tables, JSON fields and lookup indexes', async () => {
      await db.query(schema);
      const [tables] = await db.query('SELECT table_name, engine FROM information_schema.tables WHERE table_schema=?', [names.main]);
      assert.equal(tables.length, 6); assert(tables.every(row => row.ENGINE === 'InnoDB'));
      const [columns] = await db.query("SHOW COLUMNS FROM web_meeting LIKE 'participant_groups'");
      assert.equal(columns.length, 1); assert.equal(columns[0].Type, 'json');
      const [indexes] = await db.query('SHOW INDEX FROM web_meeting');
      assert(indexes.some(row => row.Key_name === 'web_meeting_room_time'));
    });
    await check('MySQL startup initializes one snapshot and all five matching projections', async () => {
      primary = await start();
      const health = await request(primary, '/__portal/health');
      assert.equal(health.data.storage, 'mysql'); assert.equal(health.headers.get('x-ttcs-storage'), 'mysql');
      const state = (await stored()).document;
      for (const [table, collection] of [['web_room','rooms'],['web_user','users'],['web_equipment','equipment'],['web_meeting','meetings'],['web_equipment_booking','equipmentBookings']]) {
        const [rows] = await db.query('SELECT COUNT(*) AS count FROM ' + table + ' WHERE is_deleted=0');
        assert.equal(Number(rows[0].count), state[collection].length, table);
      }
      const [rows] = await db.query('SELECT COUNT(*) AS count FROM web_state'); assert.equal(Number(rows[0].count), 1);
    });
    await check('Admin, PM, Member login and anonymous access behave through the MySQL runtime', async () => {
      await request(primary, '/api/demo/state', null, 'GET', undefined, 401);
      adminCookie = await login(primary, 'admin'); pmCookie = await login(primary, 'pm'); memberCookie = await login(primary, 'member');
      const state = (await api('/api/demo/state')).data;
      assert.equal(state.storage, 'mysql'); assert(!state.extra.authCredentials);
      assert((await stored()).document.extra.authCredentials.admin.hash);
      await api('/api/meetings/create', 'POST', payload(), 403, memberCookie);
    });
    await check('all 27 available module entries are served with the shared API bridge', async () => {
      const catalog = (await request(primary, '/portal/modules.json')).data;
      const available = catalog.modules.filter(module => module.available); assert.equal(available.length, 27);
      for (const module of available) {
        const response = await fetch(primary.base + module.entry, { headers: { cookie: adminCookie }, signal: AbortSignal.timeout(10000) });
        assert.equal(response.status, 200, module.id); assert.match(await response.text(), /\/portal\/demo-client.js/);
      }
    });
    await check('Vietnamese, emoji, groups, guests and UTC offsets persist correctly in JSON and SQL', async () => {
      group = (await api('/api/participant-groups')).data.find(item => item.id === 'department:IT'); assert(group);
      const participants = [...new Set(['DEMO01', 'DEMO03', ...group.memberIds])];
      meeting = (await api('/api/meetings/create', 'POST', payload({ title: 'Họp kiểm thử MySQL 🎯 — kế hoạch', description: 'Tiếng Việt có dấu và ký tự mở rộng 🚀', roomId: 'D402',
        participants: ['DEMO03'], groupIds: [group.id], participantCount: participants.length + 2,
        startTime: '2040-02-10T02:00:00Z', endTime: '2040-02-10T03:00:00Z', equipment: [{ equipmentId: 'D402-EQ5', quantity: 2 }] }), 201)).data;
      assert.deepEqual([...meeting.participants].sort(), participants.sort()); assert.equal(meeting.participantCount, participants.length + 2);
      const [rows] = await db.execute('SELECT * FROM web_meeting WHERE meeting_id=?', [meeting.id]);
      assert.equal(rows[0].title, meeting.title); assert.equal(rows[0].start_time, '2040-02-10 09:00:00');
      assert.deepEqual(rows[0].participants, meeting.participants); assert.deepEqual(rows[0].participant_groups, [group.id]);
      const state = (await stored()).document.meetings.find(item => item.meetingId === meeting.id);
      assert.equal(state.participantCount, participants.length + 2); assert.equal(state.description, meeting.description);
    });
    await check('editing an unrelated field preserves participants, guest count and equipment quantities', async () => {
      const changed = (await api('/api/meetings/' + meeting.id, 'PUT', { title: 'MySQL — chỉnh sửa giữ nguyên dữ liệu 🎯' })).data;
      assert.deepEqual(changed.participants, meeting.participants); assert.equal(changed.participantCount, meeting.participantCount);
      assert.deepEqual(changed.equipment, [{ equipmentId: 'D402-EQ5', quantity: 2 }]);
      const [rows] = await db.execute('SELECT quantity FROM web_equipment_booking WHERE meeting_id=?', [meeting.id]);
      assert.equal(rows.length, 1); assert.equal(rows[0].quantity, 2);
    });
    await check('room conflicts, invalid dates, capacity and failed participant batches leave SQL unchanged', async () => {
      await unchanged(async () => {
        await api('/api/meetings/create', 'POST', payload({ roomId: 'D402', startTime: meeting.startTime, endTime: meeting.endTime }), 409);
        await api('/api/meetings/create', 'POST', payload({ startTime: '2040-02-30T09:00:00', endTime: '2040-02-30T10:00:00' }), 400);
        await api('/api/meetings/create', 'POST', payload({ participantCount: 999 }), 409);
        await api('/api/meetings/' + meeting.id + '/participants', 'POST', { userIds: ['DEMO02', 'missing-user'] }, 404);
      });
    });
    await check('maintenance, equipment booking limits and restoration update actual MySQL records', async () => {
      await api('/api/equipment/D402-EQ6/maintenance', 'POST', { quantity: 1 });
      const booking = (await api('/api/equipment/bookings', 'POST', { meetingId: meeting.id, equipmentId: 'D402-EQ6', quantity: 2 }, 201)).data;
      await unchanged(() => api('/api/equipment/bookings/' + encodeURIComponent(booking.id), 'PUT', { quantity: 3 }, 409));
      const [items] = await db.execute('SELECT unavailable_quantity FROM web_equipment WHERE equipment_id=?', ['D402-EQ6']); assert.equal(items[0].unavailable_quantity, 1);
      await api('/api/equipment/D402-EQ6/restore', 'POST', { quantity: 1 });
      const stock = (await api('/api/equipment/status?' + new URLSearchParams({ room: 'D402', at: meeting.startTime, endTime: meeting.endTime }))).data;
      assert.equal(stock.find(item => item.equipmentId === 'D402-EQ6').availableQuantity, 1);
    });
    await check('Member visibility, own RSVP and PM ownership are enforced with persisted state', async () => {
      const privateMeeting = (await api('/api/meetings/create', 'POST', payload({ startTime: '2040-02-11T09:00:00', endTime: '2040-02-11T10:00:00' }), 201)).data;
      const visible = (await api('/api/meetings', 'GET', undefined, 200, memberCookie)).data;
      assert(visible.some(item => item.id === meeting.id)); assert(!visible.some(item => item.id === privateMeeting.id));
      await api('/api/meetings/' + meeting.id + '/participants/DEMO03', 'PATCH', { status: 'ACCEPTED' }, 200, memberCookie);
      assert.equal((await stored()).document.meetings.find(item => item.meetingId === meeting.id).participantStatuses.DEMO03, 'ACCEPTED');
      await unchanged(async () => {
        await api('/api/meetings/' + privateMeeting.id, 'GET', undefined, 403, memberCookie);
        await api('/api/meetings/' + meeting.id + '/participants/DEMO01', 'PATCH', { status: 'DECLINED' }, 403, memberCookie);
        await api('/api/meetings/' + meeting.id, 'PUT', { title: 'Forbidden' }, 403, pmCookie);
      });
    });
    await check('room grants and role matrix persist and reject unauthorized PM writes', async () => {
      const grants = (await api('/api/room-permissions')).data.rooms.map(item => item.id === 'A101' ? { ...item, roles: ['ADMIN'], version: item.version + 1 } : item);
      await api('/api/room-permissions', 'PUT', { rooms: grants });
      await unchanged(() => api('/api/meetings/create', 'POST', payload({ organizerId: 'DEMO02', participants: ['DEMO02'] }), 403, pmCookie));
      const permissions = [[true,true,true,true,true,true],[true,false,false,false,false,false],[true,false,false,false,false,false]];
      await api('/api/demo/legacy', 'POST', { key: 'permissions', value: permissions });
      assert.deepEqual((await stored()).document.extra.permissions, permissions);
      await unchanged(() => api('/api/meetings/create', 'POST', payload({ roomId: 'A102', organizerId: 'DEMO02', participants: ['DEMO02'] }), 403, pmCookie));
      permissions[1][1] = true; permissions[1][2] = true;
      await api('/api/demo/legacy', 'POST', { key: 'permissions', value: permissions });
    });
    await check('room, equipment and user CRUD sync projections and preserve soft-deleted rows', async () => {
      await api('/api/rooms', 'POST', { roomId: 'QA_ROOM', roomName: 'Phòng QA 🧪', capacity: 20, status: 'AVAILABLE', location: 'Tầng thử nghiệm' }, 201);
      await api('/api/equipment', 'POST', { equipmentId: 'QA_EQ', equipmentName: 'Thiết bị QA 🎤', roomId: 'QA_ROOM', totalQuantity: 4 }, 201);
      createdUser = (await api('/api/users', 'POST', { fullName: 'Người dùng MySQL 🧪', username: 'mysql.qa', email: 'mysql.qa@example.com', roleId: 'MEMBER', password: passwords.member }, 201)).data;
      await api('/api/rooms/QA_ROOM', 'PUT', { roomName: 'Phòng QA đã sửa 🧪', capacity: 25, status: 'AVAILABLE' });
      const [users] = await db.execute('SELECT full_name FROM web_user WHERE user_id=?', [createdUser.id]); assert.equal(users[0].full_name, createdUser.fullName);
      const [rooms] = await db.query("SELECT room_name,capacity FROM web_room WHERE room_id='QA_ROOM'"); assert.equal(rooms[0].capacity, 25); assert.match(rooms[0].room_name, /đã sửa/);
      await api('/api/equipment/QA_EQ', 'DELETE'); await api('/api/rooms/QA_ROOM', 'DELETE');
      for (const [table, key, value] of [['web_room','room_id','QA_ROOM'],['web_equipment','equipment_id','QA_EQ']]) {
        const [rows] = await db.execute('SELECT is_deleted FROM ' + table + ' WHERE ' + key + '=?', [value]); assert.equal(rows[0].is_deleted, 1);
      }
      await login(primary, 'mysql.qa', passwords.member);
    });
    await check('recurring conflicts roll back the whole series and valid series persist all occurrences', async () => {
      await api('/api/meetings/create', 'POST', payload({ startTime: '2040-03-08T13:00:00', endTime: '2040-03-08T14:00:00' }), 201);
      await unchanged(() => api('/api/meetings/recurring', 'POST', payload({ startTime: '2040-03-01T13:00:00', endTime: '2040-03-01T14:00:00', frequency: 'WEEKLY', occurrences: 3 }), 409));
      const series = (await api('/api/meetings/recurring', 'POST', payload({ startTime: '2040-04-01T13:00:00', endTime: '2040-04-01T14:00:00', frequency: 'WEEKLY', occurrences: 3 }), 201)).data;
      assert.equal(series.occurrences, 3);
      const state = (await stored()).document; assert.equal(state.meetings.filter(item => item.recurrenceSeriesId === series.seriesId).length, 3);
      const [rows] = await db.query('SELECT COUNT(*) AS count FROM web_meeting WHERE meeting_id IN (?)', [series.meetings.map(item => item.id)]); assert.equal(Number(rows[0].count), 3);
    });
    await check('a real SQL trigger failure rolls back an earlier meeting INSERT and the snapshot', async () => {
      const before = digest(await stored());
      await db.query("CREATE TRIGGER qa_fail_booking BEFORE INSERT ON web_equipment_booking FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='QA deliberate projection failure'");
      try {
        await api('/api/meetings/create', 'POST', payload({ title: 'Must roll back SQL', startTime: '2040-05-01T13:00:00', endTime: '2040-05-01T14:00:00', equipment: [{ equipmentId: 'A101-EQ5', quantity: 1 }] }), 503);
        assert.equal(digest(await stored()), before);
        const [rows] = await db.execute('SELECT COUNT(*) AS count FROM web_meeting WHERE title=?', ['Must roll back SQL']); assert.equal(Number(rows[0].count), 0);
      } finally { await db.query('DROP TRIGGER qa_fail_booking'); }
      await api('/api/meetings/create', 'POST', payload({ title: 'Recovered after SQL rollback', startTime: '2040-05-01T13:00:00', endTime: '2040-05-01T14:00:00', equipment: [{ equipmentId: 'A101-EQ5', quantity: 1 }] }), 201);
    });
    await check('restart retains credentials, Unicode, groups, guests and equipment; old sessions expire', async () => {
      const credentials = digest((await stored()).document.extra.authCredentials);
      const expired = adminCookie;
      await stop(primary); primary = await start();
      await request(primary, '/api/auth/me', expired, 'GET', undefined, 401);
      adminCookie = await login(primary, 'admin'); pmCookie = await login(primary, 'pm'); memberCookie = await login(primary, 'member');
      await login(primary, 'mysql.qa', passwords.member);
      const restored = (await api('/api/meetings/' + meeting.id)).data;
      assert.match(restored.title, /chỉnh sửa/); assert.deepEqual(restored.participants, meeting.participants);
      assert.equal(restored.participantCount, meeting.participantCount); assert.deepEqual(restored.participantGroups, [group.id]);
      assert(restored.equipment.some(item => item.equipmentId === 'D402-EQ5' && item.quantity === 2));
      assert.equal(digest((await stored()).document.extra.authCredentials), credentials);
    });
    await check('two web processes serialize concurrent account writes without losing records', async () => {
      const secondary = await start(); const secondCookie = await login(secondary, 'admin');
      try {
        const createdAccounts = await Promise.all(Array.from({ length: 8 }, (_, index) => request(index % 2 ? secondary : primary, '/api/users', index % 2 ? secondCookie : adminCookie, 'POST',
          { fullName: 'Concurrent MySQL ' + index, email: 'mysql.concurrent.' + index + '@example.com', roleId: 'MEMBER' }, 201)));
        const state = (await stored()).document;
        assert(createdAccounts.every(result => state.users.some(user => user.id === result.data.id)));
        const [rows] = await db.query('SELECT COUNT(*) AS count FROM web_user WHERE user_id IN (?)', [createdAccounts.map(result => result.data.id)]); assert.equal(Number(rows[0].count), 8);
      } finally { await stop(secondary); }
    });
    await check('SELECT FOR UPDATE blocks two processes; only one overlapping booking commits', async () => {
      const secondary = await start(); const secondCookie = await login(secondary, 'admin');
      let locked = false;
      try {
        await db.beginTransaction(); await db.query('SELECT document FROM web_state WHERE id=1 FOR UPDATE'); locked = true;
        let finished = 0;
        const body = payload({ title: 'Concurrent contested slot', startTime: '2040-06-01T13:00:00', endTime: '2040-06-01T14:00:00' });
        const pending = [request(primary, '/api/meetings/create', adminCookie, 'POST', body, null), request(secondary, '/api/meetings/create', secondCookie, 'POST', body, null)].map(promise => promise.then(value => { finished++; return value; }));
        await sleep(150); assert.equal(finished, 0, 'Writes should wait for the snapshot lock');
        await db.commit(); locked = false;
        const results = await Promise.all(pending); assert.deepEqual(results.map(result => result.status).sort(), [201,409]);
        const [rows] = await db.execute('SELECT COUNT(*) AS count FROM web_meeting WHERE title=?', [body.title]); assert.equal(Number(rows[0].count), 1);
      } finally { if (locked) await db.rollback(); await stop(secondary); }
    });
    await check('read-only requests and suggestions do not rewrite the snapshot revision', async () => {
      await unchanged(async () => {
        await api('/api/demo/state'); await api('/api/meetings');
        await api('/api/rooms/suggestions', 'POST', { attendees: 2, startTime: '2040-07-01T13:00:00', endTime: '2040-07-01T14:00:00' });
      });
    });
    await check('killing an application connection during a real lock wait returns 503 and reconnects', async () => {
      const before = digest(await stored());
      const [[self]] = await db.query('SELECT CONNECTION_ID() AS id');
      await db.beginTransaction(); await db.query('SELECT document FROM web_state WHERE id=1 FOR UPDATE');
      let pending, failure;
      try {
        pending = api('/api/rooms', 'POST', { roomId: 'QA_KILLED', roomName: 'Must not persist', capacity: 1, status: 'AVAILABLE' }, null);
        let target;
        for (let attempt = 0; attempt < 100; attempt++) {
          const [threads] = await control.execute("SELECT ID FROM information_schema.PROCESSLIST WHERE DB=? AND ID<>? AND COMMAND='Query' AND INFO LIKE 'SELECT document FROM web_state%'", [names.main, self.id]);
          if (threads.length) { target = Number(threads[0].ID); break; }
          await sleep(25);
        }
        assert(Number.isSafeInteger(target) && target > 0, 'Expected a blocked connection belonging to this test database');
        await control.query('KILL CONNECTION ' + target);
        failure = await pending;
        assert.equal(failure.status, 503);
      } finally { await db.rollback(); if (pending) await pending.catch(() => {}); }
      assert.equal(digest(await stored()), before);
      assert.equal((await request(primary, '/__portal/health')).data.storage, 'mysql');
      await api('/api/rooms', 'POST', { roomId: 'QA_RECOVERED', roomName: 'Connection recovered', capacity: 1, status: 'AVAILABLE' }, 201);
      const [rows] = await db.query("SELECT COUNT(*) AS count FROM web_room WHERE room_id='QA_KILLED'"); assert.equal(Number(rows[0].count), 0);
    });
    await check('cancellation retains history and releases all device bookings and reminders in SQL', async () => {
      await api('/api/meetings/' + meeting.id + '/reminders', 'POST', { before: 30, channels: ['APP'] }, 201);
      await api('/api/meetings/' + meeting.id, 'DELETE', { reason: 'Nghiệm thu MySQL' });
      const [bookings] = await db.execute('SELECT status FROM web_equipment_booking WHERE meeting_id=?', [meeting.id]); assert(bookings.length >= 2); assert(bookings.every(row => row.status === 'CANCELLED'));
      const [rows] = await db.execute('SELECT status FROM web_meeting WHERE meeting_id=?', [meeting.id]); assert.equal(rows[0].status, 'CANCELLED');
      const state = (await stored()).document; assert(state.reminders.filter(item => item.meetingId === meeting.id).every(item => item.status === 'CANCELLED'));
      const stock = (await api('/api/equipment/status?' + new URLSearchParams({ room: 'D402', at: meeting.startTime, endTime: meeting.endTime }))).data;
      assert.equal(stock.find(item => item.equipmentId === 'D402-EQ5').availableQuantity, 2);
    });
    await check('old schemas fail without partial writes; migration is repeatable and preserves data', async () => {
      const legacy = await createDatabase(names.legacy);
      try {
        const oldSchema = schema.replace('participant_groups JSON NOT NULL DEFAULT (JSON_ARRAY()), ', ''); assert(!oldSchema.includes('participant_groups'));
        await legacy.query(oldSchema);
        const state = await stored();
        await legacy.execute('INSERT INTO web_state(id,revision,document,updated_at) VALUES(1,?,?,NOW(3))', [state.revision, JSON.stringify(state.document)]);
        const legacyMeeting = state.document.meetings.find(item => item.meetingId === meeting.id);
        await legacy.execute('INSERT INTO web_meeting(meeting_id,room_id,organizer_id,title,description,start_time,end_time,status,participants) VALUES(?,?,?,?,?,?,?,?,?)',
          [legacyMeeting.meetingId, legacyMeeting.roomId, legacyMeeting.organizerId, legacyMeeting.title, legacyMeeting.description,
            legacyMeeting.startTime.slice(0,19).replace('T',' '), legacyMeeting.endTime.slice(0,19).replace('T',' '), legacyMeeting.status, JSON.stringify(legacyMeeting.participants)]);
        const prior = await start(names.legacy, true); assert.match(prior.output, /ER_BAD_FIELD_ERROR/);
        const [rooms] = await legacy.query('SELECT COUNT(*) AS count FROM web_room'); assert.equal(Number(rooms[0].count), 0, 'Startup projections must roll back');
        assert.equal(digest(await stored(legacy)), digest(state));
        await legacy.query(migration); await legacy.query(migration);
        const [columns] = await legacy.query("SHOW COLUMNS FROM web_meeting LIKE 'participant_groups'"); assert.equal(columns.length, 1);
        const [oldRows] = await legacy.execute('SELECT title,participants,participant_groups FROM web_meeting WHERE meeting_id=?', [meeting.id]);
        assert.equal(oldRows[0].title, legacyMeeting.title); assert.deepEqual(oldRows[0].participants, legacyMeeting.participants);
        assert.deepEqual(oldRows[0].participant_groups, [], 'Existing rows receive an empty JSON group list without losing prior fields');
        const migrated = await start(names.legacy);
        try {
          const cookie = await login(migrated, 'admin');
          const restored = (await request(migrated, '/api/meetings/' + meeting.id, cookie)).data;
          assert.deepEqual(restored.participantGroups, [group.id]); assert.equal(restored.participantCount, meeting.participantCount);
          assert.equal(digest((await stored(legacy)).document.extra.authCredentials), digest(state.document.extra.authCredentials));
        } finally { await stop(migrated); }
      } finally { await legacy.end(); }
    });
    await check('missing schema fails closed; a restored snapshot rebuilds matching projections', async () => {
      const recovery = await createDatabase(names.recovery);
      try {
        const failure = await start(names.recovery, true); assert.match(failure.output, /ER_NO_SUCH_TABLE/);
        const [tables] = await recovery.query('SHOW TABLES'); assert.equal(tables.length, 0);
        await recovery.query(schema);
        const state = await stored();
        await recovery.execute('INSERT INTO web_state(id,revision,document,updated_at) VALUES(1,?,?,NOW(3))', [state.revision, JSON.stringify(state.document)]);
        const restored = await start(names.recovery);
        try {
          const cookie = await login(restored, 'mysql.qa', passwords.member);
          assert.equal((await request(restored, '/api/auth/me', cookie)).data.user.id, createdUser.id);
          assert.equal(digest((await stored(recovery)).document), digest(state.document));
          for (const [table, collection] of [['web_room','rooms'],['web_user','users'],['web_equipment','equipment'],['web_meeting','meetings'],['web_equipment_booking','equipmentBookings']]) {
            const [rows] = await recovery.query('SELECT COUNT(*) AS count FROM ' + table + ' WHERE is_deleted=0'); assert.equal(Number(rows[0].count), state.document[collection].length);
          }
        } finally { await stop(restored); }
      } finally { await recovery.end(); }
      // Optional acceptance of an existing private file snapshot; never rewrites its source file.
      if (process.env.MYSQL_QA_STATE_FILE) {
        const sourceFile = path.resolve(process.env.MYSQL_QA_STATE_FILE);
        const originalBytes = fs.readFileSync(sourceFile);
        const originalHash = createHash('sha256').update(originalBytes).digest('hex');
        const state = JSON.parse(originalBytes.toString('utf8'));
        for (const key of ['rooms','users','equipment','meetings','equipmentBookings']) assert(Array.isArray(state[key]), 'Invalid source snapshot: ' + key);
        const snapshot = await createDatabase(names.snapshot);
        try {
          await snapshot.query(schema);
          await snapshot.execute('INSERT INTO web_state(id,revision,document,updated_at) VALUES(1,?,?,NOW(3))', [state.revision, JSON.stringify(state)]);
          const cloned = await start(names.snapshot);
          try {
            assert.equal((await request(cloned, '/__portal/health')).data.storage, 'mysql');
            const saved = (await stored(snapshot)).document;
            assert.equal(digest(saved), digest(state), 'Snapshot contents changed while restoring the private copy');
            const collections = {};
            for (const [table, key] of [['web_room','rooms'],['web_user','users'],['web_equipment','equipment'],['web_meeting','meetings'],['web_equipment_booking','equipmentBookings']]) {
              const [rows] = await snapshot.query('SELECT COUNT(*) AS count FROM ' + table + ' WHERE is_deleted=0');
              assert.equal(Number(rows[0].count), state[key].length, table); collections[key] = state[key].length;
            }
            assert.equal(digest(saved.extra.authCredentials), digest(state.extra.authCredentials));
            assert.equal(createHash('sha256').update(fs.readFileSync(sourceFile)).digest('hex'), originalHash);
            report.currentSnapshot = { passed: true, collections, sourceFileUnchanged: true, credentialsUnchanged: true, canonicalSnapshotUnchanged: true };
          } finally { await stop(cloned); }
        } finally { await snapshot.end(); }
      }
    });
    report.passed = true;
  } finally {
    for (const server of servers) await stop(server);
    if (db) await db.end();
    if (control) {
      for (const name of created) {
        try { await control.query('DROP DATABASE ' + identifier(name)); report.cleanup.push({ database: name, removed: true }); }
        catch (error) { report.passed = false; report.cleanup.push({ database: name, removed: false, code: error.code }); }
      }
      await control.end();
    }
    report.finishedAt = new Date().toISOString();
    report.passedCases = report.cases.filter(item => item.passed).length;
    report.failedCases = report.cases.filter(item => !item.passed).length;
    report.limitations = ['Node web store only; Java uses a separate schema and authentication contract', 'No production load, full penetration test, external integrations or real-device acceptance'];
    if (process.env.MYSQL_QA_REPORT) fs.writeFileSync(path.resolve(process.env.MYSQL_QA_REPORT), JSON.stringify(report, null, 2) + '\n');
    assert(report.passed && report.cleanup.every(item => item.removed), 'MySQL acceptance or cleanup failed; see the TAP results/report.');
  }
});
