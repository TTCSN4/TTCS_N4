'use strict';

// Columns match US20: view, create, book, users, reports, inventory.
const defaults = [[true,true,true,true,true,true],[true,true,true,false,false,false],[true,false,false,false,false,false]];
const roleIndex = { ADMIN: 0, PM: 1, MEMBER: 2 };
function can(state, role, column) {
  if (role === 'ADMIN') return true;
  const row = roleIndex[role];
  if (row === undefined) return false;
  return (state.extra?.permissions || defaults)[row]?.[column] === true;
}
function canModule(role, id, state = {}) {
  if (role === 'ADMIN') return true;
  if (['us11','us14','us20','us21','us28'].includes(id)) return false;
  if (['us18','us19'].includes(id)) return role === 'PM' && can(state, role, 3);
  if (['us22','us23','us24'].includes(id)) return role === 'PM' && can(state, role, 4);
  if (['us06','us17','us27'].includes(id)) return can(state, role, 0);
  if (role !== 'PM') return false;
  if (['us01','us02','us03','us04','us05','us16'].includes(id)) return can(state, role, 1);
  return can(state, role, 2);
}
function canReadMeeting(user, meeting) {
  return user.role !== 'MEMBER' || meeting.organizerId === user.id || meeting.participants.includes(user.id);
}
function assertRoomPermission(state, organizer, roomId) {
  const grant = state.roomGrants.find(item => item.id === roomId);
  if (!grant) return;
  const aliases = { ADMIN: 'ADMIN', PM: 'USER', MEMBER: 'GUEST' };
  const role = organizer.roles.includes('ADMIN') ? 'ADMIN' : organizer.roles.includes('PM') ? 'PM' : 'MEMBER';
  if (!grant.roles.includes(role) && !grant.roles.includes(aliases[role])) {
    const error = Error('Tài khoản chủ trì không có quyền đặt phòng này.'); error.status = 403; throw error;
  }
}
module.exports = { can, canModule, canReadMeeting, assertRoomPermission };
