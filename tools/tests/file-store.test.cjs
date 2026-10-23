'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const createStore = require('../web/file-store.cjs');

test('file storage retries transient Windows rename locks without losing committed data', async t => {
  const directory = fs.mkdtempSync(path.join(__dirname, '.tmp-file-'));
  const file = path.join(directory, 'state.json'), previous = process.env.DATA_FILE;
  process.env.DATA_FILE = file;
  let state = { revision: 1, rooms: [], users: [], equipment: [], meetings: [], equipmentBookings: [] };
  const api = async (req, res) => { state.revision++; state.rooms.push({ roomId: 'QA' }); res.writeHead(201, {}); res.end(JSON.stringify({ ok: true })); };
  api.auth = { initialize() {} }; api.exportState = () => structuredClone(state); api.restore = value => { state = structuredClone(value); };
  const rename = fs.promises.rename.bind(fs.promises);
  let failures = 2, calls = 0, store;
  t.mock.method(fs.promises, 'rename', async (...args) => { calls++; if (failures-- > 0) { const error = Error('OneDrive transient lock'); error.code = 'EPERM'; throw error; } return rename(...args); });
  try {
    store = await createStore(api);
    assert.equal(calls, 3);
    let status;
    await store.handle({ method: 'POST' }, { writeHead(code) { status = code; }, end() {} }, new URL('http://localhost/api/rooms'));
    assert.equal(status, 201);
    assert.equal(JSON.parse(fs.readFileSync(file, 'utf8')).rooms[0].roomId, 'QA');
  } finally {
    store?.close();
    if (previous === undefined) delete process.env.DATA_FILE; else process.env.DATA_FILE = previous;
    const absolute = path.resolve(directory), allowed = path.resolve(__dirname) + path.sep + '.tmp-file-';
    assert(absolute.startsWith(allowed)); fs.rmSync(absolute, { recursive: true, force: true });
  }
});
