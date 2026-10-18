// US02: story-local editing; shared UI and backend are unchanged.
(() => {
'use strict';
function equipmentReserved(list,id,date,start,end,ignore) {
 const events=[];
 for(const b of list) {
  if(b.id===ignore||b.status!=='confirmed'||b.date!==date||b.start>=end||b.end<=start)continue;
  const quantity=(b.equipment||[]).filter(x=>String(x.id)===String(id)).reduce((sum,x)=>sum+Number(x.quantity||0),0);
  events.push([b.start>start?b.start:start,quantity],[b.end<end?b.end:end,-quantity]);
 }
 events.sort((a,b)=>a[0].localeCompare(b[0])||a[1]-b[1]);
 let current=0,peak=0;for(const [,delta]of events){current+=delta;peak=Math.max(peak,current);}return peak;
}

const editable = b => b?.status === 'confirmed' && new Date(`${b.date}T${b.start}`) > new Date();
const latest = () => read('bookings', bookings);
function render() {
 bookings = latest();
 screen.innerHTML = panel('Lịch họp của tôi', bookingTable(true), 'Chỉ chỉnh sửa hoặc hủy cuộc họp chưa bắt đầu.');
 $$('[data-edit]').forEach(button => {
  const meeting = bookings.find(b => String(b.id) === button.dataset.edit);
  button.disabled = !editable(meeting);
  if(button.disabled) button.title = 'Cuộc họp đã bắt đầu hoặc đã hủy.';
  button.onclick = () => edit(meeting);
 });
 $$('[data-cancel]').forEach(button => button.onclick = () => cancel(bookings.find(b => String(b.id) === button.dataset.cancel)));
}
function edit(meeting) {
 if(!editable(meeting)) return toast('Cuộc họp không còn được phép chỉnh sửa.');
 const snapshot = JSON.stringify(meeting), members = read('people', people), equipment = read('equipment', []);
 dialog('Chỉnh sửa cuộc họp', bookingForm(meeting));
 const form = $('#booking-form'), details = document.createElement('div');
 details.innerHTML = `<div class="field"><label for="description">Mô tả cuộc họp</label><textarea id="description" name="description" class="input" maxlength="2000">${escapeHtml(meeting.description || '')}</textarea></div><fieldset><legend>Người tham dự</legend>${members.map(p => `<label class="chip"><input type="checkbox" name="participantId" value="${escapeHtml(p.id)}" ${(meeting.participantIds || []).map(String).includes(String(p.id)) ? 'checked' : ''}>${escapeHtml(p.name)}</label>`).join('')}</fieldset><fieldset><legend>Thiết bị và số lượng</legend>${equipment.map(item => {
  const allocation = (meeting.equipment || []).find(x => String(x.id) === String(item.id));
  return `<div class="field"><label for="equipment-${escapeHtml(item.id)}">${escapeHtml(item.name)}</label><input class="input" id="equipment-${escapeHtml(item.id)}" data-equipment-id="${escapeHtml(item.id)}" type="number" min="0" step="1" value="${allocation?.quantity || 0}"></div>`;
 }).join('') || '<p class="note">Chưa có danh mục thiết bị trong trình duyệt.</p>'}</fieldset>`;
 form.insertBefore(details, $('#form-error'));
 form.onsubmit = event => {
  event.preventDefault();
  const current = latest(), saved = current.find(b => b.id === meeting.id);
  const error = message => { $('#form-error').textContent = message; };
  if(!editable(saved) || JSON.stringify(saved) !== snapshot) return error('Lịch đã thay đổi. Đóng form và tải lại danh sách trước khi sửa.');
  const values = new FormData(form);
  const updated = {...meeting, title: values.get('title').trim(), room: values.get('room'), date: values.get('date'), start: values.get('start'), end: values.get('end'), people: Number(values.get('people')), description: values.get('description').trim(), participantIds: values.getAll('participantId')};
  bookings = current;
  const message = bookingError(updated, meeting.id);
  if(message) return error(message);
  if(updated.participantIds.length > updated.people) return error('Số người tham dự không được nhỏ hơn danh sách đã chọn.');
  const allocations = [], catalogue = read('equipment', equipment);
  for(const input of $$('[data-equipment-id]', form)) {
   const quantity = Number(input.value), item = catalogue.find(x => String(x.id) === input.dataset.equipmentId);
   const reserved = equipmentReserved(current,input.dataset.equipmentId,updated.date,updated.start,updated.end,meeting.id);
   if(!item || !Number.isInteger(quantity) || quantity < 0 || quantity > (item.status === 'ready' ? Math.max(0,item.available-reserved) : 0)) return error('Thiết bị không đủ số lượng hoặc đang bảo trì.');
   if(quantity) allocations.push({id:item.id,quantity});
  }
  updated.equipment = [...allocations,...(meeting.equipment || []).filter(x => !equipment.some(i => String(i.id) === String(x.id)))];
  if(write('bookings',current.map(b => b.id === meeting.id ? updated : b))) { closeDialog();render();toast('Đã cập nhật cuộc họp trong trình duyệt.'); }
 };
}
function cancel(meeting) {
 if(!editable(meeting)) return toast('Chỉ hủy được cuộc họp chưa bắt đầu.');
 dialog('Xác nhận hủy?',`<p>Hủy “${escapeHtml(meeting.title)}” và giải phóng phòng, thiết bị đã chọn?</p>`,'<button class="button button-danger" id="confirm-cancel">Xác nhận hủy</button>');
 $('#confirm-cancel').onclick = () => {
  const current = latest(), saved = current.find(b => b.id === meeting.id);
  if(!editable(saved)||JSON.stringify(saved)!==snapshot) return toast('Lịch đã thay đổi. Hãy tải lại danh sách.');
  if(write('bookings',current.map(b => b.id === meeting.id ? {...b,status:'cancelled'} : b))) {closeDialog();render();toast('Đã hủy cuộc họp trong trình duyệt.');}
 };
}
window.addEventListener('storage',event => {if(event.key === 'meeting-ui-bookings' && !$('.dialog')) render();});
render();
})();
