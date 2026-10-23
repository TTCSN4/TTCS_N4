'use strict';

function fail(message, status = 400) { const error = Error(message); error.status = status; throw error; }
function validDate(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(value + 'T00:00:00Z');
  return Number.isFinite(+date) && date.toISOString().slice(0, 10) === value;
}
function stamp(value) {
  if (typeof value !== 'string') return NaN;
  const match = /^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d{1,3})?)?(Z|[+-]\d{2}:\d{2})?$/.exec(value);
  if (!match || !validDate(match[1]) || +match[2] > 23 || +match[3] > 59 || +(match[4] || 0) > 59) return NaN;
  return Date.parse(value + (match[5] ? '' : '+07:00'));
}
function range(start, end) {
  const a = stamp(start), b = stamp(end);
  if (!Number.isFinite(a) || !Number.isFinite(b) || b <= a) fail('Khoảng thời gian không hợp lệ.');
  return [a, b];
}
const localTime = value => new Date(stamp(value) + 7 * 3600000).toISOString().slice(0, 19);
module.exports = { fail, validDate, stamp, range, localTime };
