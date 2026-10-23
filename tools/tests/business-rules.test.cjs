'use strict';
const { test, beforeEach, after } = require('node:test');
const assert = require('node:assert/strict');
const { Readable } = require('node:stream');
const api = require('../web/demo-api.cjs');
const auth = require('../web/auth.cjs');
const initial = api.exportState();
const password = 'QA-local-fixture-2026';
for (const role of ['ADMIN', 'PM', 'MEMBER']) process.env['INITIAL_' + role + '_PASSWORD'] = password;
let admin;
async function request(url, method = 'GET', body, cookie = admin) {
  const req = Readable.from(body === undefined ? [] : [JSON.stringify(body)]);
  Object.assign(req, { method, headers: { host: 'localhost', ...(cookie ? { cookie } : {}) }, socket: { remoteAddress: 'qa-test' } });
  let status, data, headers;
  await api(req, { writeHead(code, values) { status = code; headers = values; }, end(value) { data = JSON.parse(value); } }, new URL(url, 'http://localhost'));
  return { status, data, cookie: headers?.['Set-Cookie']?.split(';')[0] };
}
async function login(username) {
  const result = await request('/api/auth/login', 'POST', { username, password }, null);
  assert.equal(result.status, 200);
  return result.cookie;
}
beforeEach(async () => {
  api.restore(initial); api.auth = auth; auth.initialize(api); admin = await login('admin');
});
after(() => { api.restore(initial); });
const payload = (overrides = {}) => ({ title: 'QA meeting', organizerId: 'DEMO01', roomId: 'A101', participants: ['DEMO01'], startTime: '2040-02-10T13:00:00+07:00', endTime: '2040-02-10T14:00:00+07:00', equipment: [], ...overrides });
async function create(overrides) {
  const result = await request('/api/meetings/create', 'POST', payload(overrides));
  assert.equal(result.status, 201, JSON.stringify(result.data)); return result.data;
}
async function expectError(url, method, body, status, cookie) {
  const before = api.exportState();
  const result = await request(url, method, body, cookie);
  assert.equal(result.status, status, JSON.stringify(result.data));
  const after = api.exportState();
  // Actor is request-scoped identity, not business data.
  delete before.actor; delete after.actor;
  assert.deepEqual(after, before, 'Rejected writes must not change business state');
}

test('meeting edits preserve equipment quantities and cancellation releases the active reservation', async () => {
  const meeting = await create({ equipment: [{ equipmentId: 'A101-EQ5', quantity: 2 }] });
  const { equipment, ...edit } = payload();
  const updated = await request('/api/meetings/' + meeting.id, 'PUT', { ...edit, title: 'Changed only title' });
  assert.equal(updated.status, 200);
  assert.deepEqual(updated.data.equipment, [{ equipmentId: 'A101-EQ5', quantity: 2 }]);
  const bookings = api.exportState().equipmentBookings.filter(b => b.meetingId === meeting.id);
  assert.equal(new Set(bookings.map(b => b.id)).size, bookings.length);
  assert.equal((await request('/api/equipment/bookings/' + encodeURIComponent(meeting.id + ':A101-EQ5'), 'DELETE')).status, 200);
  const stock = await request('/api/equipment/status?' + new URLSearchParams({ room: 'A101', at: payload().startTime, endTime: payload().endTime }));
  assert.equal(stock.data.find(e => e.equipmentId === 'A101-EQ5').availableQuantity, 2);
});
test('equipment booking updates subtract maintenance and reject invalid or out-of-meeting time ranges', async () => {
  const meeting = await create({ equipment: [{ equipmentId: 'A101-EQ5', quantity: 1 }] });
  assert.equal((await request('/api/equipment/A101-EQ5/maintenance', 'POST', { quantity: 1 })).status, 200);
  await expectError('/api/equipment/bookings/' + encodeURIComponent(meeting.id + ':A101-EQ5'), 'PUT', { quantity: 2 }, 409);
  await expectError('/api/equipment/bookings', 'POST', { meetingId: meeting.id, equipmentId: 'A101-EQ1', quantity: 1, startTime: 'invalid', endTime: 'invalid' }, 400);
  await expectError('/api/equipment/bookings', 'POST', { meetingId: meeting.id, equipmentId: 'A101-EQ1', quantity: 1, startTime: '2040-02-10T12:00:00+07:00', endTime: payload().endTime }, 400);
});
test('participant additions are atomic, enforce capacity and cannot remove the organizer', async () => {
  const meeting = await create();
  await expectError('/api/meetings/' + meeting.id + '/participants', 'POST', { userIds: ['DEMO02', 'missing-user'] }, 404);
  const ids = api.exportState().users.filter(u => u.status === 'ACTIVE').map(u => u.id).slice(0, 11);
  await expectError('/api/meetings/' + meeting.id + '/participants', 'POST', { userIds: ids }, 409);
  await expectError('/api/meetings/' + meeting.id + '/participants/DEMO01', 'DELETE', undefined, 409);
});
test('PM cannot impersonate another organizer, modify another meeting or administer permissions', async () => {
  const meeting = await create(); const pm = await login('pm');
  await expectError('/api/meetings/create', 'POST', payload({ roomId: 'A102' }), 403, pm);
  await expectError('/api/meetings/' + meeting.id, 'DELETE', undefined, 403, pm);
  await expectError('/api/demo/legacy', 'POST', { key: 'permissions', value: [[true], [true], [true]] }, 403, pm);
  await expectError('/api/room-permissions', 'PUT', { rooms: [] }, 403, pm);
});
test('Member sees only joined meetings and can respond only to their own invitation', async () => {
  const privateMeeting = await create();
  const joined = await create({ roomId: 'A102', participants: ['DEMO01', 'DEMO03'], startTime: '2040-02-10T15:00:00+07:00', endTime: '2040-02-10T16:00:00+07:00' });
  const member = await login('member');
  const list = await request('/api/meetings', 'GET', undefined, member);
  assert(!list.data.some(m => m.id === privateMeeting.id));
  assert(list.data.some(m => m.id === joined.id));
  assert.equal((await request('/api/meetings/' + privateMeeting.id, 'GET', undefined, member)).status, 403);
  assert.equal((await request('/api/meetings/' + joined.id + '/participants/DEMO03', 'PATCH', { status: 'ACCEPTED' }, member)).status, 200);
  await expectError('/api/meetings/' + joined.id + '/participants/DEMO02', 'PATCH', { status: 'ACCEPTED' }, 403, member);
  assert.equal((await request('/api/check-in','POST',{meetingId:joined.id},member)).status,201);
  const personal=await request('/api/demo/state','GET',undefined,member);
  assert.equal(personal.data.attendance.length,1);assert.equal(personal.data.attendance[0].userId,'DEMO03');
});
test('room permissions and saved role matrix are enforced by API', async () => {
  const grants = api.exportState().roomGrants.map(r => ({ ...r, roles: r.id === 'A101' ? ['ADMIN'] : r.roles, version: r.version + (r.id === 'A101' ? 1 : 0) }));
  assert.equal((await request('/api/room-permissions', 'PUT', { rooms: grants })).status, 200);
  const pm = await login('pm');
  await expectError('/api/meetings/create', 'POST', payload({ organizerId: 'DEMO02' }), 403, pm);
  const matrix = [[true,true,true,true,true,true],[true,false,false,false,false,false],[true,false,false,false,false,false]];
  assert.equal((await request('/api/demo/legacy', 'POST', { key: 'permissions', value: matrix })).status, 200);
  await expectError('/api/meetings/create', 'POST', payload({ organizerId: 'DEMO02', roomId: 'A102' }), 403, pm);
});
test('CRUD cannot overwrite duplicate IDs, shrink committed stock or leave dangling room references', async () => {
  const meeting = await create({ participants: ['DEMO01','DEMO02'], equipment: [{ equipmentId: 'A101-EQ5', quantity: 2 }] });
  const item = api.exportState().equipment.find(e => e.equipmentId === 'A101-EQ5');
  await expectError('/api/equipment', 'POST', item, 409);
  await expectError('/api/equipment/unknown', 'PUT', { ...item, equipmentId: 'unknown' }, 404);
  await expectError('/api/equipment/A101-EQ5', 'PUT', { ...item, totalQuantity: 1 }, 409);
  const room = api.exportState().rooms.find(r => r.roomId === 'A101');
  await expectError('/api/rooms/A101', 'PUT', { ...room, capacity: 1 }, 409);
  await expectError('/api/rooms/A102', 'DELETE', undefined, 409);
  assert(meeting.id);
});
test('invalid dates, unsupported methods, JSON primitives and recurrence rules fail without state changes', async () => {
  await expectError('/api/meetings/create', 'PUT', payload(), 404);
  await expectError('/api/meetings/create', 'POST', null, 400);
  await expectError('/api/rooms/suggestions', 'POST', { startTime: 'bad', endTime: 'bad' }, 400);
  await expectError('/api/meetings/recurring', 'POST', { ...payload(), frequency: 'MONTHLY', monthDay: 0, occurrences: 2 }, 400);
  await expectError('/api/meetings/recurring', 'POST', { ...payload(), frequency: 'WEEKLY', weekdays: [9], occurrences: 2 }, 400);
  await expectError('/api/meetings/create', 'POST', payload({ startTime: '2040-02-30T13:00:00', endTime: '2040-02-30T14:00:00' }), 400);
});
test('cancelled meetings cannot be revived by general update and room cancellation releases every resource', async () => {
  const meeting = await create({ equipment: [{ equipmentId: 'A101-EQ5', quantity: 1 }] });
  assert.equal((await request('/api/room-bookings/' + meeting.id, 'DELETE')).status, 200);
  assert(api.exportState().equipmentBookings.filter(b => b.meetingId === meeting.id).every(b => b.status === 'CANCELLED'));
  await expectError('/api/meetings/' + meeting.id, 'PUT', payload({ status: 'SCHEDULED' }), 409);
});
test('usernames and emails are normalized, renaming preserves credentials and the last Admin stays active', async () => {
  let result = await request('/api/users', 'POST', { fullName: 'QA User', email: 'QaPerson@Example.com', username: 'QaPerson', password });
  assert.equal(result.status, 201);
  const id = result.data.id;
  assert.equal((await login('qaperson')).length > 0, true);
  await expectError('/api/users', 'POST', { fullName: 'Duplicate', email: 'qaperson@example.com', password }, 409);
  result = await request('/api/users/' + id, 'PUT', { fullName: 'QA User', email: 'new.qa@example.com', username: 'renamed.qa', roleId: 'MEMBER' });
  assert.equal(result.status, 200);
  assert.equal((await login('renamed.qa')).length > 0, true);
  const state = api.exportState(); state.users.forEach(u => { if (u.id !== 'DEMO01' && u.roles.includes('ADMIN')) { u.roles = ['MEMBER']; u.roleId = 'MEMBER'; } }); api.restore(state);
  await expectError('/api/users/DEMO01', 'PUT', { fullName: 'Admin', email: 'demo01@example.com', roleId: 'MEMBER' }, 409);
});
test('check-in rejects nonparticipants and duplicate actions', async () => {
  const meeting = await create();
  await expectError('/api/check-in', 'POST', { meetingId: meeting.id, userId: 'DEMO02' }, 403);
  await expectError('/api/check-in', 'POST', { meetingId: meeting.id, userId: 'unknown' }, 404);
  assert.equal((await request('/api/check-in', 'POST', { meetingId: meeting.id })).status, 201);
  await expectError('/api/check-in', 'POST', { meetingId: meeting.id }, 409);
});

test('group membership is a saved participant snapshot and explicit removal survives later edits', async () => {
  const group = api.exportState().departments.find(d => d.id === 'IT');
  const meeting = await create({ groupIds: ['department:' + group.id], roomId: 'D402' });
  assert(meeting.participants.includes('DEMO04'));
  const next = meeting.participants.filter(id => id !== 'DEMO04');
  const update = await request('/api/meetings/' + meeting.id, 'PUT', { participants: next });
  assert.equal(update.status, 200); assert(!update.data.participants.includes('DEMO04'));
  const later = await request('/api/meetings/' + meeting.id, 'PUT', { title: 'Unrelated edit' });
  assert.equal(later.status, 200); assert(!later.data.participants.includes('DEMO04'));
});
test('legacy booking adapters cannot bypass equipment stock checks', async () => {
  const meeting = await create({ equipment: [{ equipmentId: 'A101-EQ5', quantity: 1 }] });
  const bookings = api.snapshot().legacy.bookings;
  bookings.find(b => b.id === meeting.id).equipment[0].quantity = 999;
  await expectError('/api/demo/legacy', 'POST', { key: 'bookings', value: bookings }, 409);
  const list=api.snapshot().legacy.bookings;
  // US08 uses Date.now() IDs and a headcount that may include unnamed guests.
  const result=await request('/api/demo/legacy','POST',{key:'bookings',value:[...list,{id:9000000000000,title:'Legacy room booking',description:'Eight attendees',room:'A102',date:'2040-02-12',start:'13:00',end:'14:00',people:8,createdBy:'DEMO01',equipment:[],status:'confirmed'}]});
  assert.equal(result.status,200);
  const saved=api.exportState().meetings.find(m=>m.meetingId==='9000000000000');
  assert.equal(saved.participantCount,8);assert.equal(saved.description,'Eight attendees');
});
test('US16 accepts its real reminder labels and cancelled reminders are never dispatched', async () => {
  const meeting = await create();
  assert.equal((await request('/api/demo/legacy', 'POST', { key: 'notifications', value: { app: true, email: true, before: '30 phút' } })).status, 200);
  assert.equal((await request('/api/room-bookings/' + meeting.id, 'DELETE')).status, 200);
  assert.equal((await request('/api/reminders/dispatch', 'POST', {})).status, 200);
  assert(api.exportState().reminders.filter(r => r.meetingId === meeting.id).every(r => r.status === 'CANCELLED'));
});
test('PM can request room suggestions while management stays restricted', async () => {
  const pm = await login('pm');
  const result = await request('/api/rooms/suggestions', 'POST', { attendees: 2, startTime: payload().startTime, endTime: payload().endTime }, pm);
  assert.equal(result.status, 200); assert(result.data.length > 0);
  await expectError('/api/rooms/A101/status', 'POST', { status: 'MAINTENANCE' }, 403, pm);
});
test('invalid role matrices, stale room permission changes and report dates fail atomically', async () => {
  await expectError('/api/demo/legacy', 'POST', { key: 'permissions', value: [[true]] }, 400);
  const grants = api.exportState().roomGrants.map(r => ({ ...r, roles: r.id === 'A101' ? ['ADMIN'] : r.roles }));
  await expectError('/api/room-permissions', 'PUT', { rooms: grants }, 409);
  await expectError('/api/admin/reports/room-usage?from=invalid&to=invalid', 'GET', undefined, 400);
  await expectError('/api/auth/login', 'POST', null, 400, null);
});
test('recurring creation rolls back earlier occurrences when a later date conflicts', async () => {
  await create({ startTime: '2040-02-17T13:00:00+07:00', endTime: '2040-02-17T14:00:00+07:00' });
  await expectError('/api/meetings/recurring', 'POST', { ...payload(), frequency: 'WEEKLY', occurrences: 3 }, 409);
});
test('restoring old snapshots deduplicates booking keys and retains the most recent row', () => {
  const state = api.exportState(), old = state.equipmentBookings[0];
  state.equipmentBookings.push({ ...old, quantity: 2, status: 'BOOKED' });
  api.restore(state);
  assert.equal(api.exportState().equipmentBookings.filter(b => b.id === old.id).length, 1);
  assert.equal(api.exportState().equipmentBookings.find(b => b.id === old.id).quantity, 2);
});

test('attendees cannot be booked in two rooms at once, while adjacent meetings remain valid', async () => {
  await create({ participants: ['DEMO01','DEMO03'] });
  await expectError('/api/meetings/create', 'POST', payload({ organizerId: 'DEMO02', roomId: 'A102', participants: ['DEMO02','DEMO03'] }), 409);
  await create({ organizerId: 'DEMO02', roomId: 'A102', participants: ['DEMO02','DEMO03'], startTime: payload().endTime, endTime: '2040-02-10T15:00:00+07:00' });
});
test('PM can create, update and cancel their own booking with equipment', async () => {
  const pm=await login('pm'),body=payload({organizerId:'DEMO02',participants:['DEMO02'],equipment:[{equipmentId:'A101-EQ5',quantity:1}]});
  const created=await request('/api/meetings/create','POST',body,pm);assert.equal(created.status,201);
  assert.equal((await request('/api/meetings/'+created.data.id,'PUT',{title:'PM update'},pm)).status,200);
  assert.equal((await request('/api/meetings/'+created.data.id,'DELETE',undefined,pm)).status,200);
});

test('stale legacy snapshots are rejected and failed batch writes roll back every earlier item', async () => {
  const a=await create(),b=await create({startTime:'2040-02-11T13:00:00+07:00',endTime:'2040-02-11T14:00:00+07:00'});
  const revision=api.exportState().revision,bookings=api.snapshot().legacy.bookings;
  await request('/api/meetings/'+a.id,'PUT',{title:'Newer edit'});
  await expectError('/api/demo/legacy','POST',{key:'bookings',value:bookings,expectedRevision:revision},409);
  const next=api.snapshot().legacy.bookings;
  next.find(x=>x.id===a.id).title='Must roll back';next.find(x=>x.id===b.id).equipment=[{id:'A101-EQ5',quantity:999}];
  await expectError('/api/demo/legacy','POST',{key:'bookings',value:next},409);
});

test('a Member with revoked view permission can load the portal without receiving calendar data', async () => {
  const matrix=[[true,true,true,true,true,true],[true,true,true,false,false,false],[false,false,false,false,false,false]];
  assert.equal((await request('/api/demo/legacy','POST',{key:'permissions',value:matrix})).status,200);
  const member=await login('member'),snapshot=await request('/api/demo/state','GET',undefined,member);
  assert.equal(snapshot.status,200);assert.deepEqual(snapshot.data.allowedModules,[]);assert.deepEqual(snapshot.data.meetings,[]);assert.deepEqual(snapshot.data.legacy.bookings,[]);
  assert.equal((await request('/api/meetings','GET',undefined,member)).status,403);
});
