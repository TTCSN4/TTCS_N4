// US02 — feature/US02-update-cancel-meeting-frontend
let meetingSearch = '';
const normalizeMeetingSearch = value => String(value ?? '').toLocaleLowerCase('vi')
  .normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd');

function matchingBookings(query) {
  const terms = normalizeMeetingSearch(query).trim().split(/\s+/).filter(Boolean);
  return bookings.filter(booking => {
    const room = rooms.find(item => item.id === booking.room);
    const attendeeIds = [...(booking.participantIds || []), booking.organizerId];
    const attendees = attendeeIds.map(id => {
      const person = people.find(item => String(item.id) === String(id));
      return [id, person?.name, person?.email].join(' ');
    }).join(' ');
    const text = normalizeMeetingSearch([
      booking.id, booking.title, booking.room, room?.name,
      booking.date, booking.start, booking.end, attendees
    ].join(' '));
    return terms.every(term => text.includes(term));
  });
}

function renderBookingResults() {
  const visible = matchingBookings(meetingSearch);
  $('#booking-results').innerHTML = visible.length
    ? bookingTable(STORY === 'US02', visible)
    : '<p class="empty">Không tìm thấy cuộc họp phù hợp. Hãy thử từ khóa khác.</p>';
  $('#booking-result-count').textContent = `${visible.length} / ${bookings.length} cuộc họp`;
  $('#clear-meeting-search').hidden = !meetingSearch;
  bindBookings(us02);
}

function us02() {
  const active = bookings.filter(booking => booking.status === 'confirmed');
  const search = `<div class="toolbar meeting-search-toolbar" role="search" aria-label="Tìm cuộc họp để chỉnh sửa hoặc hủy">
    <label for="meeting-search" class="sr">Tìm cuộc họp</label>
    <input id="meeting-search" class="input" type="search" value="${escapeHtml(meetingSearch)}" placeholder="Tìm theo tên, mã cuộc họp, phòng hoặc người tham gia…" aria-describedby="meeting-search-hint" aria-controls="booking-results">
    <button type="button" id="clear-meeting-search" class="button"${meetingSearch ? '' : ' hidden'}>Xóa tìm kiếm</button>
    <span id="booking-result-count" class="note" role="status" aria-live="polite"></span>
  </div><p id="meeting-search-hint" class="note meeting-search-hint">Có thể tìm không dấu theo tên, mã, phòng, ngày họp, tên hoặc email người tham gia.</p><div id="booking-results"></div>`;
  screen.innerHTML = `<div class="stats">${[
    [active.length, 'Lịch đã xác nhận'],
    [bookings.filter(booking => booking.status === 'cancelled').length, 'Lịch đã hủy'],
    [new Set(active.map(booking => booking.room)).size, 'Phòng đang sử dụng']
  ].map(([count, label]) => `<div class="stat"><div class="stat-value">${count}</div><div class="stat-label">${label}</div></div>`).join('')}</div>${panel('Lịch họp của tôi', search, 'Chỉnh sửa hoặc hủy cuộc họp chưa diễn ra.')}`;
  $('#meeting-search').addEventListener('input', event => {
    meetingSearch = event.target.value;
    renderBookingResults();
  });
  $('#clear-meeting-search').addEventListener('click', () => {
    meetingSearch = '';
    $('#meeting-search').value = '';
    renderBookingResults();
    $('#meeting-search').focus();
  });
  renderBookingResults();
}
us02();
