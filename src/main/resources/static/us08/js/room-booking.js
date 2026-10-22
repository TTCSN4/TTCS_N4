// US08 uses the existing demo data contract without changing the shared UI.
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

let members = read('people',people);
let actorId = String(read('active-user-id',members.find(p => p.role === 'Người đặt lịch' && p.status !== 'Tạm khóa')?.id || ''));
function permissionError(roomId) {
 members = read('people',members);
 const actor = members.find(p => String(p.id) === actorId);
 if(!actor || actor.status === 'Tạm khóa') return 'Tài khoản không hoạt động hoặc không tồn tại.';
 const roles=['Admin','Người đặt lịch','Người tham dự'], index=roles.indexOf(actor.role);
 const defaults=[[true,true,true,true,true,true],[true,true,true,false,false,false],[true,false,false,false,false,false]];
 const matrix=read('permissions',defaults);
 if(index<0 || (actor.role !== 'Admin' && matrix?.[index]?.[2] !== true)) return 'Vai trò của bạn chưa được cấp quyền đặt phòng.';
 // US21 persists room grants with stable room IDs and legacy role codes.
 let grants;
 try {grants=(window.TTCSDemo?{rooms:window.TTCSDemo.state.roomGrants}:JSON.parse(localStorage.getItem('meeting-us21-permissions-v1') || 'null'))?.rooms?.find(r=>r.id===roomId)?.roles;} catch {return 'Không đọc được cấu hình quyền phòng. Vui lòng kiểm tra lại US21.';}
 const code=['ADMIN','USER','GUEST'][index];
 if(grants && !grants.includes(code)) return 'Bạn không được phép đặt phòng này. Hãy chọn phòng khác.';
 return '';
}
function render() {
 const draft=read('booking-draft',null), equipment=read('equipment',[]);
 screen.innerHTML=panel('Thông tin đặt phòng',`${select('Người đặt lịch dùng thử','booking-user',members.map(p=>String(p.id)),actorId)}${bookingForm()}<p class="note">Quyền được đọc từ US20 và US21 trong cùng trình duyệt.</p>`,'Chọn phòng và thời gian; kiểm tra quyền, sức chứa và lịch trùng trước khi lưu.');
 const actorSelect=$('#booking-user');actorSelect.replaceChildren(...members.map(p=>{const o=document.createElement('option');o.value=String(p.id);o.textContent=`${p.name} · ${p.role}`;o.selected=o.value===actorId;return o;}));
 actorSelect.onchange=()=>{actorId=actorSelect.value;write('active-user-id',actorId);$('#form-error').textContent='';};
 const form=$('#booking-form'), extras=document.createElement('div');
 extras.innerHTML=`<div class="field"><label for="description">Mô tả cuộc họp</label><textarea id="description" name="description" class="input" maxlength="2000"></textarea></div><fieldset><legend>Thiết bị (tùy chọn)</legend>${equipment.map(item=>`<div class="field"><label for="equipment-${escapeHtml(item.id)}">${escapeHtml(item.name)} · ${item.available} sẵn có</label><input class="input" id="equipment-${escapeHtml(item.id)}" data-equipment-id="${escapeHtml(item.id)}" type="number" value="0" min="0" step="1" ${item.status!=='ready'?'disabled':''}></div>`).join('')||'<p class="note">Chưa có danh mục thiết bị trong trình duyệt.</p>'}</fieldset>`;
 form.insertBefore(extras,$('#form-error'));
 if(draft) for(const [key,value] of Object.entries(draft)) if(form.elements[key] && typeof value!=='object') form.elements[key].value=value;
 form.onsubmit=event=>{
  event.preventDefault();
  const values=new FormData(form), nextBooking={title:values.get('title').trim(),room:values.get('room'),date:values.get('date'),start:values.get('start'),end:values.get('end'),people:Number(values.get('people')),description:values.get('description').trim(),equipment:[]};
  const error=message=>{$('#form-error').textContent=message;};
  bookings=read('bookings',bookings);
  const message=permissionError(nextBooking.room)||bookingError(nextBooking);
  if(message) return error(message);
  const catalogue=read('equipment',equipment);
  for(const input of $$('[data-equipment-id]',form)) {
   const quantity=Number(input.value), item=catalogue.find(x=>String(x.id)===input.dataset.equipmentId);
   const reserved=equipmentReserved(bookings,input.dataset.equipmentId,nextBooking.date,nextBooking.start,nextBooking.end);
   if(!item||!Number.isInteger(quantity)||quantity<0||quantity>(item.status==='ready'?Math.max(0,item.available-reserved):0)) return error('Thiết bị không đủ số lượng hoặc đang bảo trì.');
   if(quantity) nextBooking.equipment.push({id:item.id,quantity});
  }
  const saved={...nextBooking,id:Date.now(),createdBy:actorId,status:'confirmed'};
  if(write('bookings',[...bookings,saved])) {bookings=[...bookings,saved];try{localStorage.removeItem('meeting-ui-booking-draft');}catch{}dialog('Đặt phòng thành công',`<p>${escapeHtml(saved.title)} · ${escapeHtml(saved.room)}</p><p>${saved.date}, ${saved.start} – ${saved.end}</p><p class="note">Lịch đã lưu trong trình duyệt.</p>`);}
 };
}
render();
})();
