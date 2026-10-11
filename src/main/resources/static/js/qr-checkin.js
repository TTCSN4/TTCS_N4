(function () {
  'use strict';
  const $ = id => document.getElementById(`qr-${id}`);
  const seed = () => [
    { code: 'MEETING-001', title: 'Họp nhóm dự án Website', room: 'Phòng A101', organizer: 'Nguyễn Văn B', date: '10/10/2026', time: '09:00 – 10:30', attendees: 8, state: 'scheduled', inTime: null, outTime: null },
    { code: 'MEETING-002', title: 'Họp kế hoạch Sprint', room: 'Phòng Ocean · P.201', organizer: 'Nguyễn Văn C', date: '10/10/2026', time: '10:00 – 11:00', attendees: 6, state: 'checked_in', inTime: '10/10/2026, 10:00', outTime: null },
    { code: 'MEETING-003', title: 'Họp với khách hàng', room: 'Phòng Sakura · P.102', organizer: 'Nguyễn Văn D', date: '10/10/2026', time: '14:00 – 15:00', attendees: 5, state: 'cancelled', inTime: null, outTime: null }
  ];
  let meetings = seed(), selected = null, stream = null, frame = null, scanning = false, cameraToken = 0, lookupToken = 0, busy = false;
  const labels = { scheduled: 'Chờ check-in', checked_in: 'Đang sử dụng', checked_out: 'Đã check-out', cancelled: 'Đã hủy' };
  const timestamp = () => new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'short' }).format(new Date());
  function notice(text, error = false) { $('alert').textContent = text; $('alert').hidden = false; $('alert').dataset.error = String(error); $('alert').setAttribute('role', error ? 'alert' : 'status'); }
  function stopCamera() {
    cameraToken++; scanning = false; if (frame !== null) cancelAnimationFrame(frame);
    if (stream) stream.getTracks().forEach(track => track.stop()); stream = null;
    $('video').srcObject = null; $('video').hidden = true; $('placeholder').hidden = false;
    $('scan-line').hidden = true; $('stop').hidden = false; $('stop').disabled = true; $('camera').disabled = false; $('camera').textContent = '◎ Mở camera';
  }
  function render() {
    $('details').hidden = !selected; $('empty').hidden = !!selected;
    if (!selected) { $('checkin').disabled = true; $('checkout').disabled = true; $('current-state').textContent = 'Chưa chọn cuộc họp'; $('guidance').textContent = 'Chọn cuộc họp để xác nhận thao tác.'; return; }
    for (const [id, value] of Object.entries({ room: selected.room, title: selected.title, 'meeting-code': selected.code, organizer: selected.organizer, date: selected.date, time: selected.time, attendees: `${selected.attendees} người`, 'in-time': selected.inTime || 'Chưa check-in', 'out-time': selected.outTime || 'Chưa check-out' })) $(id).textContent = value;
    $('current-state').textContent = labels[selected.state]; $('current-state').dataset.state = selected.state; $('status').textContent = selected.state === 'scheduled' ? 'Đã lên lịch' : labels[selected.state]; $('status').dataset.state = selected.state;
    $('checkin').disabled = busy || selected.state !== 'scheduled'; $('checkout').disabled = busy || selected.state !== 'checked_in';
    $('guidance').textContent = { scheduled: 'Xác nhận check-in khi bạn có mặt tại phòng họp.', checked_in: 'Cuộc họp đang sử dụng phòng. Xác nhận check-out khi kết thúc.', checked_out: 'Đã hoàn tất ghi nhận sử dụng phòng cho cuộc họp này.', cancelled: 'Cuộc họp đã bị hủy. Không thể check-in hoặc check-out.' }[selected.state];
  }
  async function lookup(raw) {
    if (busy) return;
    const code = String(raw || '').trim().toUpperCase().replace('MM-DEMO-', 'MEETING-'), token = ++lookupToken;
    selected = null; render(); $('alert').hidden = true; $('code-error').hidden = true; $('code').setAttribute('aria-invalid', 'false');
    if (!code) { $('code-error').textContent = 'Vui lòng nhập mã QR hoặc mã cuộc họp.'; $('code-error').hidden = false; $('code').setAttribute('aria-invalid', 'true'); $('code').focus(); return; }
    stopCamera(); $('empty').hidden = true; $('loading').hidden = false; $('find').disabled = true;
    await new Promise(resolve => setTimeout(resolve, 350));
    if (token !== lookupToken) return;
    selected = meetings.find(meeting => meeting.code === code) || null;
    $('loading').hidden = true; $('find').disabled = false; render();
    if (!selected) { $('code-error').textContent = 'Không tìm thấy cuộc họp. Hãy kiểm tra mã hoặc chọn mã mẫu.'; $('code-error').hidden = false; $('code').setAttribute('aria-invalid', 'true'); }
    else notice('Đã tìm thấy cuộc họp');
  }
  async function detector() {
    if (!('BarcodeDetector' in window)) throw new Error('Trình duyệt chưa hỗ trợ đọc QR tự động. Vui lòng nhập mã cuộc họp.');
    const formats = await window.BarcodeDetector.getSupportedFormats();
    if (!formats.includes('qr_code')) throw new Error('Trình duyệt chưa hỗ trợ QR. Vui lòng nhập mã cuộc họp.');
    return new window.BarcodeDetector({ formats: ['qr_code'] });
  }
  $('camera').addEventListener('click', async () => {
    stopCamera(); const token = cameraToken; $('camera').disabled = true; $('camera').textContent = 'Đang mở camera…';
    try {
      if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) throw new Error('Camera cần HTTPS hoặc localhost. Bạn có thể nhập mã để tiếp tục.');
      const reader = await detector(); if (token !== cameraToken) return;
      const acquired = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false });
      if (token !== cameraToken) { acquired.getTracks().forEach(track => track.stop()); return; }
      stream = acquired; $('video').srcObject = stream; await $('video').play();
      if (token !== cameraToken) return;
      $('video').hidden = false; $('placeholder').hidden = true; $('scan-line').hidden = false; $('stop').hidden = false; $('stop').disabled = false; scanning = true;
      notice('Camera đã mở. Đưa mã QR vào khung quét.');
      async function scan() {
        if (!scanning || token !== cameraToken) return;
        try {
          if ($('video').readyState >= 2) {
            const results = await reader.detect($('video'));
            if (!scanning || token !== cameraToken) return;
            if (results.length) { $('code').value = results[0].rawValue; await lookup(results[0].rawValue); return; }
          }
        } catch { stopCamera(); notice('Không thể đọc hình ảnh camera. Hãy nhập mã cuộc họp.', true); return; }
        frame = requestAnimationFrame(scan);
      }
      frame = requestAnimationFrame(scan);
    } catch (error) {
      if (token !== cameraToken) return;
      stopCamera(); notice(error.name === 'NotAllowedError' ? 'Quyền camera bị từ chối. Cho phép camera hoặc nhập mã cuộc họp.' : error.message || 'Không thể mở camera.', true);
    }
  });
  $('stop').addEventListener('click', stopCamera);
  $('image').addEventListener('change', async () => {
    const file = $('image').files[0]; if (!file || busy) return;
    stopCamera(); let bitmap;
    try {
      if (file.size > 10 * 1024 * 1024) throw new Error('Vui lòng chọn ảnh nhỏ hơn 10 MB.');
      const reader = await detector(); bitmap = await createImageBitmap(file);
      const results = await reader.detect(bitmap);
      if (!results.length) throw new Error('Không tìm thấy QR trong ảnh. Bạn có thể nhập mã thủ công.');
      $('code').value = results[0].rawValue; await lookup(results[0].rawValue);
    } catch (error) { notice(error.message || 'Không thể đọc ảnh QR.', true); }
    finally { bitmap?.close(); $('image').value = ''; }
  });
  $('form').addEventListener('submit', event => { event.preventDefault(); lookup($('code').value); });
  document.querySelectorAll('[data-demo]').forEach(button => button.addEventListener('click', () => { $('code').value = button.dataset.demo; lookup(button.dataset.demo); }));
  let action = null, actionMeeting = null;
  function confirmAction(kind) {
    if (busy || !selected || selected.state !== (kind === 'in' ? 'scheduled' : 'checked_in')) return;
    action = kind; actionMeeting = selected;
    $('dialog-title').textContent = kind === 'in' ? 'Xác nhận check-in' : 'Xác nhận check-out';
    $('dialog-message').textContent = `${selected.title} · ${selected.room}. ${kind === 'in' ? 'Bắt đầu ghi nhận sử dụng phòng?' : 'Kết thúc ghi nhận sử dụng phòng?'}`;
    $('dialog').returnValue = ''; $('dialog').showModal();
  }
  $('checkin').addEventListener('click', () => confirmAction('in')); $('checkout').addEventListener('click', () => confirmAction('out'));
  $('dialog').addEventListener('close', async () => {
    if ($('dialog').returnValue !== 'confirm' || !actionMeeting || busy) return;
    const meeting = actionMeeting, kind = action;
    if (meeting.state !== (kind === 'in' ? 'scheduled' : 'checked_in')) return;
    busy = true; render(); $('reset').disabled = true;
    notice('Đang xác nhận thao tác…');
    await new Promise(resolve => setTimeout(resolve, 600));
    const now = timestamp(); meeting.state = kind === 'in' ? 'checked_in' : 'checked_out';
    if (kind === 'in') meeting.inTime = now; else meeting.outTime = now;
    const item = document.createElement('li'); item.textContent = `${now} · ${kind === 'in' ? 'Check-in' : 'Check-out'} · ${meeting.title} · ${meeting.room}`;
    if ($('history').children.length === 1 && !$('history').children[0].dataset.record) $('history').replaceChildren();
    item.dataset.record = 'true'; item.dataset.action = kind; $('history').prepend(item);
    busy = false; actionMeeting = null; $('reset').disabled = false; render(); notice(`${kind === 'in' ? 'Check-in' : 'Check-out'} thành công. Đã ghi nhận thời gian trong phiên demo.`);
  });
  $('reset').addEventListener('click', () => {
    if (busy) return; lookupToken++; stopCamera(); meetings = seed(); selected = null; actionMeeting = null;
    $('code').value = ''; $('code-error').hidden = true; $('code').setAttribute('aria-invalid', 'false'); $('loading').hidden = true; $('find').disabled = false;
    const item = document.createElement('li'); item.textContent = 'Chưa có thao tác check-in / check-out.'; $('history').replaceChildren(item); render(); notice('Đã đặt lại dữ liệu mẫu.');
  });
  function showSampleHistory() {
    $('history').replaceChildren();
    const entries = [
      ['in', '09/10/2026 · 09:00', 'MEETING-002 · Họp kế hoạch Sprint'],
      ['out', '09/10/2026 · 10:30', 'MEETING-002 · Họp kế hoạch Sprint'],
      ['in', '08/10/2026 · 09:00', 'MEETING-003 · Họp với khách hàng'],
      ['out', '08/10/2026 · 10:15', 'MEETING-003 · Họp với khách hàng'],
      ['in', '07/10/2026 · 09:01', 'MEETING-001 · Họp nhóm dự án Website']
    ];
    for (const [kind, time, title] of entries) {
      const item = document.createElement('li');
      item.dataset.record = 'true'; item.dataset.action = kind; item.dataset.sample = 'true';
      const heading = document.createElement('strong'); heading.textContent = kind === 'in' ? 'Check-in thành công' : 'Check-out thành công';
      const detail = document.createElement('small'); detail.textContent = title;
      const date = document.createElement('time'); date.textContent = time;
      item.append(heading, detail, date); $('history').append(item);
    }
  }
  showSampleHistory();
  $('code').value = 'MEETING-001'; lookup('MEETING-001');
  window.addEventListener('pagehide', stopCamera);
  document.addEventListener('visibilitychange', () => { if (document.hidden) stopCamera(); });
})();
