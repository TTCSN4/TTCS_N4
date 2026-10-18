// US14 API adapter: DTO names and status values match the existing backend.
(() => {
'use strict';
const content=document.querySelector('#content'), overlay=document.querySelector('#overlay'), toast=document.querySelector('#toast');
const API='/api/equipment', statuses={AVAILABLE:'Sẵn sàng',BOOKED:'Đã được đặt',IN_USE:'Đang sử dụng',MAINTENANCE:'Bảo trì',BROKEN:'Hỏng'};
let items=[],query='',status='all',busy=false;
const esc=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function notify(message){toast.textContent=message;toast.setAttribute('role','status');}
async function request(path='',options={}){
 const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),15000);
 try{
  const response=await fetch(API+path,{...options,credentials:'same-origin',signal:controller.signal,headers:{Accept:'application/json',...(options.body?{'Content-Type':'application/json'}:{}),...options.headers}});
  const text=await response.text();let body=null;try{body=text?JSON.parse(text):null;}catch{if(response.ok)throw Error('API trả dữ liệu không hợp lệ.');}
  if(!response.ok)throw Error(body?.message||body?.detail||`Yêu cầu thất bại (HTTP ${response.status}).`);
  return body;
 }catch(e){if(e.name==='AbortError')throw Error('Máy chủ chưa phản hồi. Kiểm tra dữ liệu trước khi thử lại.');throw e;}
 finally{clearTimeout(timer);}
}
const payload=item=>({equipmentName:item.equipmentName.trim(),type:item.type.trim(),roomId:item.roomId||null,totalQuantity:item.totalQuantity,status:item.status});
async function refresh(){
 content.setAttribute('aria-busy','true');
 try{
  const result=await request();if(!Array.isArray(result))throw Error('Danh sách thiết bị không hợp lệ.');items=result;
  // A missing booking check is unknown, never treated as permission to modify.
  await Promise.all(items.map(async item=>{try{const value=await request(`/${encodeURIComponent(item.equipmentId)}/booked`);item.booked=typeof value==='boolean'?value:null;}catch{item.booked=null;}}));
  render();
 }catch(e){content.innerHTML='<h1>Quản lý thiết bị</h1><p id="load-error" role="alert"></p><button id="retry" class="button">Thử tải lại</button>';document.querySelector('#load-error').textContent=e.message;document.querySelector('#retry').onclick=refresh;}
 finally{content.removeAttribute('aria-busy');}
}
function render(){
 const filtered=items.filter(i=>`${i.equipmentName} ${i.type} ${i.roomId||''}`.toLocaleLowerCase('vi').includes(query.toLocaleLowerCase('vi'))&&(status==='all'||i.status===status));
 content.innerHTML=`<div class="heading"><div><h1>Quản lý thiết bị</h1><p class="subtitle">Dữ liệu từ hệ thống. Số lượng trống theo khung giờ được kiểm tra khi đặt thiết bị.</p></div><button id="add" class="button button-primary">Thêm thiết bị</button></div><section class="panel pad"><div class="toolbar"><input id="search" class="control" aria-label="Tìm thiết bị" placeholder="Tìm tên, loại hoặc mã phòng" value="${esc(query)}"><select id="status-filter" class="control" aria-label="Trạng thái"><option value="all">Tất cả trạng thái</option>${Object.entries(statuses).map(([value,label])=>`<option value="${value}" ${value===status?'selected':''}>${label}</option>`).join('')}</select></div><p>${filtered.length} thiết bị</p><div class="table-wrap"><table><thead><tr><th>THIẾT BỊ</th><th>LOẠI</th><th>MÃ PHÒNG</th><th>TỔNG SỐ LƯỢNG</th><th>TRẠNG THÁI</th><th>THAO TÁC</th></tr></thead><tbody>${filtered.map(i=>`<tr><td>${esc(i.equipmentName)}</td><td>${esc(i.type)}</td><td>${esc(i.roomId||'Thiết bị dùng chung')}</td><td>${esc(i.totalQuantity)}</td><td>${esc(statuses[i.status]||i.status)}${i.booked===true?'<div>Đang được đặt</div>':i.booked===null?'<div>Chưa xác minh lượt đặt</div>':''}</td><td><button class="button" data-edit="${esc(i.equipmentId)}" ${i.booked!==false?'disabled':''}>Sửa</button><button class="button button-danger" data-delete="${esc(i.equipmentId)}" ${i.booked!==false?'disabled':''}>Xóa</button></td></tr>`).join('')||'<tr><td colspan="6">Không có thiết bị phù hợp.</td></tr>'}</tbody></table></div></section>`;
 document.querySelector('#search').oninput=e=>{query=e.target.value;render();const input=document.querySelector('#search');input.focus();input.setSelectionRange(query.length,query.length);};
 document.querySelector('#status-filter').onchange=e=>{status=e.target.value;render();};
 document.querySelector('#add').onclick=()=>edit();
 content.querySelectorAll('[data-edit]').forEach(b=>b.onclick=()=>edit(b.dataset.edit));
 content.querySelectorAll('[data-delete]').forEach(b=>b.onclick=()=>remove(b.dataset.delete));
}
function close(){if(!busy)overlay.replaceChildren();}
function modal(title,body){
 overlay.innerHTML=`<div class="backdrop"><section class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title"><h2 id="modal-title">${esc(title)}</h2>${body}<p id="modal-error" class="error" role="alert"></p><button type="button" class="button" data-close>Đóng</button></section></div>`;
 overlay.querySelector('[data-close]').onclick=close;overlay.querySelector('.backdrop').onclick=e=>{if(e.target===e.currentTarget)close();};overlay.querySelector('input,button')?.focus();
}
async function edit(id){
 try{
  const item=id?await request(`/${encodeURIComponent(id)}`):{equipmentName:'',type:'',roomId:'',totalQuantity:1,status:'AVAILABLE'};
  if(id&&await request(`/${encodeURIComponent(id)}/booked`)!==false)return notify('Không thể sửa thiết bị đang được đặt hoặc chưa xác minh được lượt đặt.');
  modal(id?'Cập nhật thiết bị':'Thêm thiết bị',`<form id="item-form" class="form"><div class="field"><label for="name">Tên thiết bị</label><input id="name" class="control" name="equipmentName" value="${esc(item.equipmentName)}" required maxlength="255"></div><div class="field"><label for="type">Loại thiết bị</label><input id="type" class="control" name="type" value="${esc(item.type)}" required maxlength="100"></div><div class="field"><label for="room-id">Mã phòng (để trống nếu dùng chung)</label><input id="room-id" class="control" name="roomId" value="${esc(item.roomId)}" maxlength="50"></div><div class="field"><label for="total">Tổng số lượng</label><input id="total" class="control" name="totalQuantity" type="number" min="0" step="1" value="${esc(item.totalQuantity)}" required></div><div class="field"><label for="equipment-status">Trạng thái</label><select id="equipment-status" class="control" name="status">${Object.entries(statuses).map(([value,label])=>`<option value="${value}" ${value===item.status?'selected':''}>${label}</option>`).join('')}</select></div><button class="button button-primary">${id?'Lưu thay đổi':'Thêm thiết bị'}</button></form>`);
  document.querySelector('#item-form').onsubmit=async e=>{
   e.preventDefault();if(busy)return;const form=e.currentTarget,values=Object.fromEntries(new FormData(form));values.totalQuantity=Number(values.totalQuantity);
   if(!values.equipmentName.trim()||!values.type.trim()||!Number.isInteger(values.totalQuantity)||values.totalQuantity<0)return document.querySelector('#modal-error').textContent='Nhập tên, loại và số lượng nguyên không âm.';
   busy=true;const button=form.querySelector('button');button.disabled=true;
   try{await request(id?`/${encodeURIComponent(id)}`:'',{method:id?'PUT':'POST',body:JSON.stringify(payload(values))});busy=false;close();await refresh();notify('Đã lưu thiết bị vào hệ thống.');}
   catch(error){document.querySelector('#modal-error').textContent=error.message;}
   finally{busy=false;button.disabled=false;}
  };
 }catch(e){notify(e.message);}
}
function remove(id){
 const item=items.find(i=>i.equipmentId===id);if(!item||item.booked!==false)return;
 modal('Xóa thiết bị?',`<p>Xóa “${esc(item.equipmentName)}” khỏi danh mục?</p><button id="confirm-delete" class="button button-danger">Xóa thiết bị</button>`);
 document.querySelector('#confirm-delete').onclick=async e=>{
  if(busy)return;busy=true;const button=e.currentTarget;button.disabled=true;
  try{if(await request(`/${encodeURIComponent(id)}/booked`)!==false)throw Error('Không thể xóa thiết bị đang được đặt hoặc chưa xác minh được lượt đặt.');await request(`/${encodeURIComponent(id)}`,{method:'DELETE'});busy=false;close();await refresh();notify('Đã xóa thiết bị.');}
  catch(error){document.querySelector('#modal-error').textContent=error.message;}
  finally{busy=false;button.disabled=false;}
 };
}
const globalSearch=document.querySelector('#global-search');if(globalSearch)globalSearch.oninput=e=>{query=e.target.value;render();};
refresh();
})();
