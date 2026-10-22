'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const net = require('node:net');
const { spawn } = require('node:child_process');
const { randomBytes } = require('node:crypto');
const root = path.resolve(__dirname, '../..');

test('standalone startup, 27 modules, shared data, permissions and restart persistence', async t => {
  const directory = fs.mkdtempSync(path.join(__dirname, '.tmp-'));
  const passwords = Object.fromEntries(['admin', 'pm', 'member'].map(role => [role, randomBytes(18).toString('hex')]));
  let child, base, output = '';
  async function start() {
    const probe = net.createServer();
    await new Promise(resolve => probe.listen(0, '127.0.0.1', resolve));
    const port = probe.address().port;
    await new Promise(resolve => probe.close(resolve));
    base = 'http://127.0.0.1:' + port;
    const env = { ...process.env, PORT: String(port), DATA_FILE: path.join(directory, 'state.json'), INITIAL_ADMIN_PASSWORD: passwords.admin, INITIAL_PM_PASSWORD: passwords.pm, INITIAL_MEMBER_PASSWORD: passwords.member };
    for (const key of ['DEMO_MODE', 'DATA_STORE', 'APP_MODE']) delete env[key];
    child = spawn(process.execPath, [path.join(root, 'server.cjs')], { cwd: root, env, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
    child.stdout.on('data', data => { output += data; });
    child.stderr.on('data', data => { output += data; });
    for (let tries = 0; tries < 100; tries++) {
      if (child.exitCode !== null) throw Error('Startup failed: ' + output);
      try { if ((await fetch(base + '/__portal/health')).ok) return; } catch {}
      await new Promise(resolve => setTimeout(resolve, 50));
    }
    throw Error('Startup timed out: ' + output);
  }
  async function stop() {
    if (!child || child.exitCode !== null) return;
    const exited = new Promise(resolve => child.once('exit', resolve));
    child.kill(); await exited;
  }
  async function request(url, cookie, method = 'GET', body, expected = 200) {
    const response = await fetch(base + url, { method, headers: { ...(cookie ? { cookie } : {}), ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}) }, body: body === undefined ? undefined : JSON.stringify(body) });
    const data = await response.json();
    assert.equal(response.status, expected, method + ' ' + url + ': ' + JSON.stringify(data));
    return data;
  }
  async function login(role) {
    const response = await fetch(base + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: role, password: passwords[role] }) });
    assert.equal(response.status, 200, 'Login ' + role);
    return response.headers.get('set-cookie').split(';')[0];
  }
  try {
    await start();
    await t.test('one-command startup enables local API and blocks anonymous access', async () => {
      assert.equal((await request('/__portal/health')).storage, 'file');
      await request('/api/demo/state', null, 'GET', undefined, 401);
      assert.equal((await fetch(base + '/', { redirect: 'manual' })).status, 302);
    });
    let admin = await login('admin');
    await t.test('all module entries and their local assets are available', async () => {
      const catalog = await request('/portal/modules.json');
      const available = catalog.modules.filter(m => m.available);
      assert.equal(available.length, 27);
      assert.equal(catalog.modules.find(m => m.id === 'us15').available, false);
      for (const module of available) {
        const response = await fetch(base + module.entry, { headers: { cookie: admin } });
        assert.equal(response.status, 200, module.id);
        const html = await response.text();
        assert.match(html, /\/portal\/demo-client.js/);
        for (const match of html.matchAll(/(?<![:\w])(?:src|href)=["']([^"']+)["']/g)) {
          if (/^(data:|https?:|#|mailto:)/.test(match[1])) continue;
          const asset = new URL(match[1], base + module.entry);
          if (asset.pathname.endsWith('.html')) continue;
          const result = await fetch(asset, { headers: { cookie: admin } });
          assert.equal(result.status, 200, module.id + ': ' + asset.pathname);
        }
      }
    });
    const date = new Date(Date.now() + 60 * 86400000).toISOString().slice(0, 10);
    const startTime = date + 'T13:00:00', endTime = date + 'T14:00:00';
    let meeting, user;
    await t.test('meeting and equipment create, overlap rejection, edit and stock restoration', async () => {
      const payload = { title: 'Integration meeting', organizerId: 'DEMO01', roomId: 'A101', participants: ['DEMO01', 'DEMO02'], startTime, endTime, equipment: [{ equipmentId: 'A101-EQ5', quantity: 1 }] };
      meeting = await request('/api/meetings/create', admin, 'POST', payload, 201);
      await request('/api/meetings/create', admin, 'POST', payload, 409);
      const stock = () => request('/api/equipment/status?' + new URLSearchParams({ room: 'A101', at: startTime + '+07:00' }), admin);
      assert.equal((await stock()).find(e => e.equipmentId === 'A101-EQ5').availableQuantity, 1);
      await request('/api/meetings/' + meeting.id, admin, 'PUT', { ...payload, title: 'Updated meeting', equipment: [{ equipmentId: 'A101-EQ5', quantity: 2 }] });
      assert.equal((await stock()).find(e => e.equipmentId === 'A101-EQ5').availableQuantity, 0);
      await request('/api/meetings/' + meeting.id, admin, 'PUT', { ...payload, equipment: [{ equipmentId: 'A101-EQ5', quantity: 99 }] }, 409);
      assert.equal((await stock()).find(e => e.equipmentId === 'A101-EQ5').availableQuantity, 0);
      await request('/api/equipment/bookings/' + encodeURIComponent(meeting.id + ':A101-EQ5'), admin, 'DELETE');
      assert.equal((await stock()).find(e => e.equipmentId === 'A101-EQ5').availableQuantity, 2);
      await request('/api/equipment/bookings', admin, 'POST', { meetingId: meeting.id, equipmentId: 'A101-EQ5', quantity: 1 }, 201);
      const state = await request('/api/demo/state', admin);
      assert(state.legacy.bookings.some(b => b.id === meeting.id));
      user = await request('/api/users', admin, 'POST', { fullName: 'Integration User', email: 'integration-' + Date.now() + '@example.com', roleId: 'MEMBER', password: passwords.member }, 201);
      assert((await request('/api/demo/state', admin)).legacy.people.some(p => p.id === user.id));
      const login = await request('/api/auth/login', null, 'POST', { username: user.email, password: passwords.member });
      assert.equal(login.user.id, user.id);
    });
    await t.test('admin, PM and member permissions remain enforced', async () => {
      const member = await login('member'), pm = await login('pm');
      await request('/api/meetings', member);
      await request('/api/rooms', member, 'GET', undefined, 403);
      await request('/api/meetings/create', member, 'POST', {}, 403);
      await request('/api/rooms', pm, 'POST', {}, 403);
      assert.equal((await fetch(base + '/us11/templates/rooms.html', { headers: { cookie: pm } })).status, 403);
      const state = await request('/api/demo/state', member);
      assert(!state.extra.authCredentials);
      assert(!JSON.stringify(state).includes('"hash":'));
    });
    await t.test('restart preserves records and login hashes without reinitialization', async () => {
      await stop(); await start(); admin = await login('admin');
      assert.equal((await request('/api/meetings/' + meeting.id, admin)).id, meeting.id);
      assert((await request('/api/users', admin)).some(u => u.id === user.id));
      assert((await request('/api/equipment/bookings', admin)).some(b => b.meetingId === meeting.id && b.status === 'BOOKED'));
      await request('/api/meetings/' + meeting.id, admin, 'DELETE');
      assert.equal((await request('/api/meetings/' + meeting.id, admin)).status, 'CANCELLED');
      assert.equal((await request('/api/auth/login', null, 'POST', { username: user.email, password: passwords.member })).user.id, user.id);
    });
    await t.test('failed disk writes roll back the API state', async () => {
      const before = await request('/api/demo/state', admin);
      const file = path.join(directory, 'state.json');
      fs.renameSync(file, file + '.before'); fs.mkdirSync(file);
      try {
        await request('/api/users', admin, 'POST', { fullName: 'Must roll back', email: 'rollback@example.com', roleId: 'MEMBER' }, 503);
        const after = await request('/api/demo/state', admin);
        assert.equal(after.revision, before.revision);
        assert.equal(after.users.length, before.users.length);
      } finally { fs.rmdirSync(file); fs.renameSync(file + '.before', file); }
    });
    await t.test('concurrent requests preserve both records', async () => {
      const accounts = await Promise.all([1, 2].map(n => request('/api/users', admin, 'POST', { fullName: 'Concurrent ' + n, email: 'concurrent-' + n + '@example.com', roleId: 'MEMBER' }, 201)));
      const users = await request('/api/users', admin);
      assert(accounts.every(account => users.some(u => u.id === account.id)));
    });
  } finally {
    await stop();
    fs.rmSync(directory, { recursive: true, force: true });
  }
});
