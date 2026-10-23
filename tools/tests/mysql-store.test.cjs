'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const createStore = require('../web/mysql-store.cjs');

test('MySQL initialization releases its connection before closing the pool on schema failure', async () => {
  const calls = [];
  const connection = {
    async beginTransaction() { calls.push('begin'); },
    async execute() { throw Error('Missing schema'); },
    async rollback() { calls.push('rollback'); },
    release() { calls.push('release'); }
  };
  const pool = { async getConnection() { return connection; }, async end() { assert(calls.includes('release')); calls.push('end'); } };
  await assert.rejects(createStore({ exportState: () => ({ revision: 1 }) }, { pool }), /Missing schema/);
  assert.deepEqual(calls, ['begin','rollback','release','end']);
});
test('MySQL closes the pool when acquiring the initial connection fails', async () => {
  let closed = false;
  const pool = { async getConnection() { throw Error('Database unavailable'); }, async end() { closed = true; } };
  await assert.rejects(createStore({}, { pool }), /Database unavailable/);
  assert(closed);
});
test('MySQL transaction rolls back failed projections, restores state and serves the next request', async () => {
  const seed = { revision: 1, rooms: [], users: [], equipment: [], meetings: [], equipmentBookings: [] };
  let state = structuredClone(seed), saved = structuredClone(seed), failWrite = false, releases = 0, rollbacks = 0;
  const connection = {
    async beginTransaction() {},
    async execute(sql, args) { if (sql.startsWith('UPDATE web_state')) saved = JSON.parse(args[0]); },
    async query(sql) {
      if (sql.startsWith('SELECT document')) return [[{ document: structuredClone(saved) }]];
      if (failWrite && sql.startsWith('INSERT INTO web_room')) throw Error('Projection failed');
      return [[], []];
    },
    async commit() {}, async rollback() { rollbacks++; }, release() { releases++; }
  };
  const pool = { async getConnection() { return connection; }, async end() {} };
  const api = async (req, res) => {
    if (req.method === 'POST') { state.rooms.push({ roomId: 'QA', roomName: 'QA', capacity: 1, status: 'AVAILABLE' }); state.revision++; }
    res.writeHead(200, {}); res.end(JSON.stringify(state));
  };
  api.exportState = () => structuredClone(state); api.restore = next => { state = structuredClone(next); };
  const store = await createStore(api, { pool });
  async function send(method) { let status, data; await store({ method }, { writeHead(code) { status = code; }, end(value) { data = JSON.parse(value); } }, new URL('http://localhost/api/demo/state')); return { status, data }; }
  failWrite = true;
  assert.equal((await send('POST')).status, 503);
  assert.deepEqual(state, seed); assert.deepEqual(saved, seed); assert.equal(rollbacks, 1);
  failWrite = false;
  const next = await send('POST'); assert.equal(next.status, 200); assert.equal(next.data.rooms.length, 1); assert.equal(saved.revision, 2);
  assert.equal(releases, 3);
});
