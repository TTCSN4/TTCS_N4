// US09: cancel the shared browser booking; reservations derive from active bookings.
(() => {
'use strict';
const canCancel=b=>b?.status==='confirmed'&&new Date(`${b.date}T${b.start}`)>new Date();
function render(){
 bookings=read('bookings',bookings);
 screen.innerHTML=panel('Lịch đặt phòng',bookingTable(false),'Chỉ hủy được cuộc họp chưa bắt đầu. Phòng và thiết bị được giải phóng sau khi xác nhận.')+panel('Trạng thái khung giờ',`<div class="list">${bookings.map(b=>`<div class="list-row"><div>${escapeHtml(rooms.find(r=>r.id===b.room)?.name||b.room)} · ${escapeHtml(b.date)}<div class="cell-sub">${escapeHtml(b.start)} – ${escapeHtml(b.end)}</div>${b.cancellationReason?`<div class="cell-sub">Lý do: ${escapeHtml(b.cancellationReason)}</div>`:''}${b.status==='cancelled'&&(b.equipment||[]).length?'<div class="cell-sub">Đã giải phóng thiết bị của lượt đặt này.</div>':''}</div><span class="status ${roomFree(b.room,b.date,b.start,b.end)?'success':'warning'}">${roomFree(b.room,b.date,b.start,b.end)?'Còn trống':'Đã có lịch'}</span></div>`).join('')}</div>`);
 $$('[data-cancel]').forEach(button=>{
  const meeting=bookings.find(b=>String(b.id)===button.dataset.cancel);
  button.disabled=!canCancel(meeting);
  button.onclick=()=>confirm(meeting);
 });
}
function confirm(meeting){
 if(!canCancel(meeting))return toast('Lịch đã bắt đầu hoặc đã hủy.');
 const snapshot=JSON.stringify(meeting);
 dialog('Xác nhận hủy phòng?',`<p>Hủy “${escapeHtml(meeting.title)}” tại ${escapeHtml(meeting.room)}, ${escapeHtml(meeting.date)}, ${escapeHtml(meeting.start)} – ${escapeHtml(meeting.end)}?</p><p>Phòng và thiết bị của lượt đặt này sẽ được giải phóng.</p><div class="field"><label for="cancel-reason">Lý do hủy (tùy chọn)</label><textarea id="cancel-reason" class="input" maxlength="500"></textarea></div><p id="cancel-error" class="error" role="alert"></p>`,'<button class="button button-danger" id="confirm-cancel">Xác nhận hủy</button>');
 $('#confirm-cancel').onclick=()=>{
  const current=read('bookings',bookings),saved=current.find(b=>b.id===meeting.id);
  if(!canCancel(saved)||JSON.stringify(saved)!==snapshot){$('#cancel-error').textContent='Lịch đã thay đổi. Đóng hộp thoại và tải lại danh sách trước khi hủy.';return;}
  const next=current.map(b=>b.id===meeting.id?{...b,status:'cancelled',cancellationReason:$('#cancel-reason').value.trim(),cancelledAt:new Date().toISOString()}:b);
  if(write('bookings',next)){bookings=next;closeDialog();render();toast('Đã hủy và giải phóng phòng, thiết bị trong dữ liệu trình duyệt.');}
 };
}
window.addEventListener('storage',event=>{if(event.key==='meeting-ui-bookings'&&!$('.dialog'))render();});
render();
})();
