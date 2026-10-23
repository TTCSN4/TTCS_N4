'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { Readable } = require('node:stream');
const api = require('../web/demo-api.cjs');
const source = fs.readFileSync(path.join(__dirname, '../../src/main/resources/static/us01/js/create-meeting.js'), 'utf8');

async function request(url, { method = 'GET', body } = {}) {
  const req = Readable.from(body === undefined ? [] : [JSON.stringify(body)]);
  req.method = method;
  let status, data;
  await api(req, { writeHead(code) { status = code; }, end(value) { data = JSON.parse(value); } }, new URL(url, 'http://localhost'));
  assert(status >= 200 && status < 300, JSON.stringify(data));
  return { status, data };
}

function editingUI(state, send = request) {
  const elements = new Map();
  const $ = id => {
    if (!elements.has(id)) elements.set(id, { value: '', textContent: '' });
    return elements.get(id);
  };
  const context = vm.createContext({
    state, $, editingId: state.meetingId, document: { querySelector: $ }, request: send,
    idValue: id => typeof id === 'string' && id.length > 0 && id.length <= 50,
    validateList: data => { assert(Array.isArray(data)); return data; },
    syncControls() { state.locked = state.editingLoading; },
    previewParticipantGroup() {}, renderPeople() {}, renderRooms() {}, refreshEquipment: async () => {},
    showErrors(errors) { state.errors = errors; }, status(message, error) { state.message = message; state.error = error; },
    applyMeeting(meeting) { state.participants = new Set(meeting.participants); }
  });
  const pending = source.slice(source.indexOf('  function addPendingParticipantSelections()'), source.indexOf('  function renderParticipantGroups()'));
  const load = source.slice(source.indexOf('  async function loadEditing()'), source.indexOf('  window.US01 ='));
  vm.runInContext(pending + '\n' + load, context);
  return { $, context };
}

test('selected group survives create, edit, participant reload and storage restoration', async () => {
  const before = api.exportState();
  try {
    const groups = (await request('/api/participant-groups')).data;
    const group = groups.find(item => item.id === 'department:IT');
    const expected = [...new Set(['DEMO01', ...group.memberIds])];
    const state = { participants: new Set(), participantGroups: groups, groupsLoading: false };
    const { $, context } = editingUI(state);
    $('participantGroup').value = group.id;
    assert.equal(vm.runInContext('addPendingParticipantSelections()', context), true);
    assert.deepEqual([...state.participants].sort(), [...group.memberIds].sort());
    assert.equal($('participantGroup').value, '');

    const body = {
      title: 'Saved technical group', organizerId: 'DEMO01', roomId: 'B201',
      startTime: '2040-01-16T09:00:00', endTime: '2040-01-16T10:00:00',
      participants: [...state.participants], equipment: []
    };
    const created = (await request('/api/meetings/create', { method: 'POST', body })).data;
    const url = '/api/meetings/' + created.meetingId;
    assert.deepEqual([...created.participants].sort(), [...expected].sort());

    // Editing unrelated fields must preserve the saved group, even if the caller omits participants.
    const { participants, ...update } = body;
    const edited = (await request(url, { method: 'PUT', body: { ...update, title: 'Changed title' } })).data;
    assert.deepEqual([...edited.participants].sort(), [...expected].sort());
    const stored = api.exportState();
    api.restore(before);
    api.restore(stored);
    assert.deepEqual((await request(url + '/participants')).data.map(person => person.userId).sort(), [...expected].sort());

    // A separate participant endpoint remains authoritative if meeting detail omits its list.
    const editState = { meetingId: created.meetingId, ready: true, userDirectory: [], participants: new Set() };
    const edit = editingUI(editState, async (path, options) => {
      const result = await request(path, options);
      if (path === url) delete result.data.participants;
      return result;
    });
    const loading = vm.runInContext('loadEditing()', edit.context);
    assert.equal(editState.editingLoading, true);
    assert.equal(editState.locked, true);
    await loading;
    assert.equal(editState.error, undefined);
    assert.equal(editState.editingLoading, false);
    assert.deepEqual([...editState.participants].sort(), [...expected].sort());
    assert.equal(editState.userDirectory.length, expected.length);

    // Explicit removal stays supported; preserving omitted fields must not restore removed people.
    const remaining = expected.filter(id => id !== 'DEMO04');
    await request(url, { method: 'PUT', body: { ...update, participants: remaining } });
    assert.deepEqual((await request(url)).data.participants.sort(), [...remaining].sort());
  } finally {
    api.restore(before);
  }
});

test('editing blocks saving when neither endpoint can supply the saved participants', async () => {
  const state = { meetingId: 'MISSING-LIST', ready: true, userDirectory: [], participants: new Set() };
  const ui = editingUI(state, async url => {
    if (url.endsWith('/participants')) throw Error('Unavailable');
    return { data: { organizerId: 'DEMO01', status: 'SCHEDULED' } };
  });
  await vm.runInContext('loadEditing()', ui.context);
  assert.equal(state.ready, false);
  assert.equal(state.error, true);
  assert.equal(state.participants.size, 0);
  assert.equal(state.editingLoading, false);
});
