// US20 owns role assignment and the browser permission matrix.
(() => {
'use strict';
const roles=['Admin','Người đặt lịch','Người tham dự'];
const permissions=['Xem lịch họp','Tạo lịch họp','Đặt phòng','Quản lý người dùng','Xem báo cáo','Quản lý phòng'];
const defaults=roles.map((_,j)=>permissions.map((_,i)=>j===0||j===1&&i<3||j===2&&i===0));
const raw=read('permissions',defaults);
let matrix=defaults.map((row,j)=>row.map((value,i)=>j===0?true:typeof raw?.[j]?.[i]==='boolean'?raw[j][i]:value));
let sourcePeople=JSON.stringify(read('people',people)), sourceMatrix=JSON.stringify(read('permissions',defaults)), dirty=false;
people=read('people',people);
function render(){
 screen.innerHTML=`${panel('Ma trận quyền truy cập',`<div class="table-wrap"><table class="matrix"><thead><tr><th>CHỨC NĂNG</th>${roles.map(r=>`<th>${r}</th>`).join('')}</tr></thead><tbody>${permissions.map((p,i)=>`<tr><td>${p}</td>${roles.map((r,j)=>`<td><input type="checkbox" data-permission="${j}-${i}" aria-label="${r}: ${p}" ${matrix[j][i]?'checked':''} ${j===0?'disabled':''}></td>`).join('')}</tr>`).join('')}</tbody></table></div><div class="actions"><button id="save-roles" class="button button-primary">Lưu phân quyền</button></div>`,'Admin luôn giữ toàn bộ quyền. Quyền đặt phòng được US08 và US21 đọc từ cấu hình đã lưu.')}<div class="grid" style="margin-top:16px">${panel('Gán vai trò cho người dùng',`<div class="list">${people.map(p=>`<div class="list-row"><div><div class="cell-main">${escapeHtml(p.name)}</div><div class="cell-sub">${escapeHtml(p.dept)} · ${escapeHtml(p.status)}</div></div><select class="select" data-user-role="${escapeHtml(p.id)}" aria-label="Vai trò ${escapeHtml(p.name)}">${!roles.includes(p.role)?'<option value="" selected disabled>Chọn vai trò hợp lệ</option>':''}${roles.map(r=>`<option value="${r}" ${r===p.role?'selected':''}>${r}</option>`).join('')}</select></div>`).join('')}</div><div class="actions"><button id="save-users" class="button button-primary">Lưu vai trò người dùng</button></div>`,'Giữ ít nhất một Admin đang hoạt động.','span-7')}${panel('Xem trước quyền truy cập',`${select('Vai trò xem trước','preview-role',roles,roles[1])}<div id="preview" class="chips"></div><p class="note">Xem trước phản ánh bản đang chỉnh sửa. Cần lưu để áp dụng.</p>`,'','span-5')}</div><p id="permission-status" role="status"></p>`;
 function preview(){const j=roles.indexOf($('#preview-role').value);$('#preview').innerHTML=permissions.filter((_,i)=>matrix[j][i]).map(p=>`<span class="chip">${p}</span>`).join('')||'<p class="empty">Vai trò chưa được cấp quyền.</p>';}
 $$('[data-permission]').forEach(box=>box.onchange=()=>{const [j,i]=box.dataset.permission.split('-').map(Number);matrix[j][i]=box.checked;dirty=true;preview();});
 $$('[data-user-role]').forEach(input=>input.onchange=()=>{dirty=true;});
 $('#preview-role').onchange=preview;
 $('#save-roles').onclick=()=>{
  if(JSON.stringify(read('permissions',defaults))!==sourceMatrix)return toast('Quyền đã thay đổi ở cửa sổ khác. Tải lại trang trước khi lưu.');
  matrix[0]=permissions.map(()=>true);
  if(write('permissions',matrix)){sourceMatrix=JSON.stringify(matrix);$('#permission-status').textContent='Đã lưu ma trận quyền trong trình duyệt.';updateDirty();}
 };
 $('#save-users').onclick=()=>{
  const current=read('people',people);
  if(JSON.stringify(current)!==sourcePeople)return toast('Danh sách người dùng đã thay đổi. Tải lại trang trước khi lưu.');
  const next=current.map(p=>({...p,role:$$('[data-user-role]').find(input=>input.dataset.userRole===String(p.id)).value}));
  if(next.some(p=>!roles.includes(p.role)))return toast('Chọn vai trò hợp lệ cho mọi người dùng.');
  if(!next.some(p=>p.role==='Admin'&&p.status==='Hoạt động'))return toast('Phải giữ ít nhất một tài khoản Admin đang hoạt động.');
  if(write('people',next)){people=next;sourcePeople=JSON.stringify(next);$('#permission-status').textContent='Đã lưu vai trò người dùng trong trình duyệt.';updateDirty();}
 };
 function updateDirty(){dirty=JSON.stringify(matrix)!==sourceMatrix||$$('[data-user-role]').some(input=>people.find(p=>String(p.id)===input.dataset.userRole)?.role!==input.value);}
 preview();
}
window.addEventListener('beforeunload',event=>{if(dirty){event.preventDefault();event.returnValue='';}});
render();
})();
