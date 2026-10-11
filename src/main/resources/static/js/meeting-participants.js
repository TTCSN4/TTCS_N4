// US04 — feature/US04-meeting-participants-frontend
function us04(){
 const active=bookings.filter(b=>b.status==='confirmed');
 if(!active.length){screen.innerHTML=panel('Mời người tham dự','<p class="empty">Chưa có cuộc họp đang xác nhận. Tạo lịch đặt phòng trước khi mời người tham dự.</p>');return}
 let meetingId=String(active[0].id),q='';
 const invitations=read('invitations',{});
 let invited=invitations[meetingId]??[1,2];
 function render(){
  const available=people.filter(p=>p.status==='Hoạt động'&&!invited.includes(p.id)&&`${p.name} ${p.email}`.toLocaleLowerCase('vi').includes(q.toLocaleLowerCase('vi')));
  screen.innerHTML=`<div class="field"><label for="invite-meeting">Cuộc họp</label><select id="invite-meeting" class="select">${active.map(b=>`<option value="${b.id}" ${String(b.id)===meetingId?'selected':''}>${escapeHtml(b.title)} · ${b.date}</option>`).join('')}</select></div><div class="grid">${panel('Tìm người tham dự',`<div class="toolbar"><input class="input" id="people-search" aria-label="Tìm người tham dự" placeholder="Nhập tên hoặc email…" value="${escapeHtml(q)}"></div><div class="list">${available.map(p=>`<div class="list-row"><div class="person"><div class="person-avatar">${initials(p.name)}</div><div><div class="cell-main">${p.name}</div><div class="cell-sub">${p.email}</div></div></div><button class="button button-primary" data-invite="${p.id}">Mời</button></div>`).join('')||'<div class="empty">Không tìm thấy người phù hợp.</div>'}</div>`,'Tìm theo họ tên hoặc email.','span-7')}${panel('Danh sách được mời',`<div class="list">${invited.map(id=>people.find(p=>p.id===id)).filter(Boolean).map(p=>`<div class="list-row"><div><div class="cell-main">${p.name}</div><div class="cell-sub">${p.email}</div><span class="status warning">Chờ phản hồi</span></div><button class="button button-danger" data-remove="${p.id}">Xóa</button></div>`).join('')||'<p class="empty">Chưa chọn người tham dự.</p>'}</div><div class="actions"><button id="send-invites" class="button button-primary" ${!invited.length?'disabled':''}>Gửi lời mời (${invited.length})</button></div>`,'Kiểm tra danh sách trước khi gửi.','span-5')}</div>`;
  $('#invite-meeting').onchange=e=>{invitations[meetingId]=[...invited];meetingId=e.target.value;invited=invitations[meetingId]??[];q='';render()};
  $('#people-search').oninput=e=>{q=e.target.value;render();$('#people-search').focus();$('#people-search').setSelectionRange(q.length,q.length)};
  $$('[data-invite]').forEach(b=>b.onclick=()=>{invited.push(Number(b.dataset.invite));render()});
  $$('[data-remove]').forEach(b=>b.onclick=()=>{invited=invited.filter(id=>id!==Number(b.dataset.remove));render()});
  $('#send-invites').onclick=()=>{invitations[meetingId]=[...invited];if(write('invitations',invitations))toast(`Đã lưu ${invited.length} lời mời minh họa cho cuộc họp; chưa gửi email.`)};
 }
 render();
}
us04();
