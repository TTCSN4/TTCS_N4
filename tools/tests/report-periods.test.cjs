'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { cancellationPeriods } = require('../../src/main/resources/static/us23/js/cancelled-meeting-statistics.js');
test('six-month cancellation reports include the current month and distinguish previous years', () => {
  assert.deepEqual(cancellationPeriods(6,new Date(2026,0,15)),['2025-08','2025-09','2025-10','2025-11','2025-12','2026-01']);
});
test('annual cancellation reports contain exactly the selected calendar year', () => {
  const months=cancellationPeriods(12,new Date(2026,9,23));
  assert.equal(months.length,12);assert.equal(months[0],'2026-01');assert.equal(months[11],'2026-12');
});
