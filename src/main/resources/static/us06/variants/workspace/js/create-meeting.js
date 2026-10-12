"use strict";
(() => {
 const $=id=>document.getElementById(id),form=$('createMeetingForm'),API='/api/meetings';
 let rooms=[],schedules=[],busy=false,ready=false;
 const fields=['meetingTitle','meetingDate','meetingRoom','startTime','endTime','participantEmails','meetingNotes'];
 const participantEmails=()=>$('participantEmails').value.split(/[,;\n]+/).map(s=>s.trim()).filter(Boolean);
 const days=['Thứ 2','Thứ 3','Thứ 4','Thứ 5','Thứ 6','Thứ 7','Chủ nhật'];
 function status(text,error=false){$('formStatus').hidden=false;$('formStatus').dataset.error=String(error);$('formStatus').textContent=text;}
 function lock(value){busy=value;form.querySelectorAll('input,select,textarea,button').forEach(el=>el.disabled=value||el.name==='equipment');$('submitBtn').disabled=value||!ready;}
 async function api(path='',options={}){
  const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),15000);
  try{const response=await fetch(path==='/rooms'?'/api/rooms':API+path,{...options,signal:controller.signal,headers:{Accept:'application/json',...(options.body?{'Content-Type':'application/json'}:{})},credentials:'same-origin'});
   if(response.status===204)return null;
   const text=await response.text();let body;try{body=text?JSON.parse(text):null;}catch(_){throw Error('Máy chủ trả dữ liệu không hợp lệ.');}
   if(!response.ok)throw Error(body?.message||body?.detail||('API trả lỗi HTTP '+response.status));
   return body;
  }catch(e){if(e.name==='AbortError')throw Error('Máy chủ chưa phản hồi sau 15 giây. Kiểm tra danh sách trước khi thử tạo lại.');if(e instanceof TypeError)throw Error('Không kết nối được backend. Kiểm tra ứng dụng cổng 8080.');throw e;}
  finally{clearTimeout(timer);}
 }
 function recurrence(){
  const frequency=$('repeatFrequency').value;if(frequency==='NONE')return null;
  return {frequency,interval:Number($('repeatInterval').value),weekdays:frequency==='WEEKLY'?[...form.querySelectorAll('[name=weekday]:checked')].map(el=>Number(el.value)):null,monthDay:frequency==='MONTHLY'?Number($('monthDay').value):null,until:$('repeatEndMode').value==='UNTIL'?$('repeatUntil').value||null:null,count:$('repeatEndMode').value==='COUNT'?Number($('repeatCount').value):null};
 }
 function repeatError(r){
  if(!r)return '';
  if(!Number.isInteger(r.interval)||r.interval<1||r.interval>52)return 'Khoảng cách lặp phải từ 1 đến 52.';
  if(r.frequency==='WEEKLY'&&!r.weekdays.length)return 'Chọn ít nhất một thứ trong tuần.';
  if(r.frequency==='MONTHLY'&&(!Number.isInteger(r.monthDay)||r.monthDay<1||r.monthDay>31))return 'Ngày trong tháng phải từ 1 đến 31.';
  if($('repeatEndMode').value==='COUNT'&&(!Number.isInteger(r.count)||r.count<1||r.count>100))return 'Số cuộc họp phải từ 1 đến 100.';
  if($('repeatEndMode').value==='UNTIL'&&(!r.until||r.until<$('meetingDate').value))return 'Ngày kết thúc phải từ ngày bắt đầu trở đi.';
  return '';
 }
 function summary(){
  $('repeatStart').value=$('meetingDate').value; $('repeatUnit').textContent=$('repeatFrequency').value==='MONTHLY'?'tháng':'tuần vào các ngày';
  const minutes=(Date.parse('2000-01-01T'+$('endTime').value)-Date.parse('2000-01-01T'+$('startTime').value))/60000; $('summaryDuration').textContent=minutes>0?minutes+' phút':'Chưa chọn'; $('summaryEquipment').textContent=[...form.querySelectorAll('[name=equipment]:checked')].map(e=>e.value).join(', ')||'Chưa chọn';
  const r=recurrence();$('repeatConfig').hidden=!r;$('weeklyConfig').hidden=!r||r.frequency!=='WEEKLY';$('monthlyConfig').hidden=!r||r.frequency!=='MONTHLY';$('countConfig').hidden=$('repeatEndMode').value!=='COUNT';$('untilConfig').hidden=$('repeatEndMode').value!=='UNTIL';$('repeatUntil').min=$('meetingDate').value;
  $('repeatSummary').textContent=r?'Cuộc họp lặp lại mỗi '+(r.interval||'…')+' '+(r.frequency==='WEEKLY'?'tuần vào '+(r.weekdays.map(d=>days[d-1]).join(' và ')||'các thứ chưa chọn'):'tháng vào ngày '+(r.monthDay||'…'))+'. Bắt đầu '+($('meetingDate').value||'chưa chọn')+'; '+(r.until?'đến '+r.until:(r.count||'…')+' cuộc họp')+'.':'Cuộc họp không lặp lại.';
  $('repeatError').textContent=repeatError(r);
  $('summaryTitle').textContent=$('meetingTitle').value.trim()||'Cuộc họp mới của bạn';$('summaryDate').textContent=$('meetingDate').value||'Chưa chọn';$('summaryTime').textContent=$('startTime').value+' – '+$('endTime').value;$('summaryRoom').textContent=rooms.find(r=>r.id===$('meetingRoom').value)?.name||'Chưa chọn';$('summaryCount').textContent=Math.max(1,participantEmails().length)+' người';
 }
 function payload(){return {title:$('meetingTitle').value.trim(),room:$('meetingRoom').value,startTime:$('meetingDate').value+'T'+$('startTime').value+':00+07:00',endTime:$('meetingDate').value+'T'+$('endTime').value+':00+07:00',participantCount:Math.max(1,participantEmails().length),emails:$('participantEmails').value.split(/[,;\n]+/).map(s=>s.trim()).filter(Boolean),notes:$('meetingNotes').value.trim(),equipment:[...form.querySelectorAll('[name=equipment]:checked')].map(el=>el.value),recurrence:recurrence()};}
 function validate(p){
  const errors={};if(p.title.length<3||p.title.length>120)errors.meetingTitle='Tên cuộc họp cần từ 3 đến 120 ký tự.';
  if(!Number.isFinite(Date.parse(p.startTime))||Date.parse(p.startTime)<=Date.now())errors.meetingDate='Chọn ngày và giờ bắt đầu trong tương lai.';
  if(!Number.isFinite(Date.parse(p.endTime))||Date.parse(p.endTime)<=Date.parse(p.startTime))errors.endTime='Giờ kết thúc phải sau giờ bắt đầu.';
  const room=rooms.find(r=>r.id===p.room);if(!room)errors.meetingRoom='Chọn phòng họp.';
  if(!Number.isInteger(p.participantCount)||p.participantCount<1||p.participantCount>(room?.capacity||30))errors.participantEmails='Số email vượt sức chứa phòng.';
  if(p.emails.length>p.participantCount||p.emails.some(e=>e.length>254||!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e))||new Set(p.emails.map(e=>e.toLowerCase())).size!==p.emails.length)errors.participantEmails='Email không hợp lệ, trùng hoặc vượt số người.';
  fields.forEach(id=>{$(id+'Error').textContent=errors[id]||'';$(id).setAttribute('aria-invalid',String(!!errors[id]));});
  if(Object.keys(errors).length){$(Object.keys(errors)[0]).focus();return false;}
  const error=repeatError(p.recurrence);$('repeatError').textContent=error;return !error;
 }
 function render(){
  $('meetingBadge').textContent=schedules.length;$('savedList').replaceChildren();
  if(!schedules.length){const p=document.createElement('p');p.textContent='Chưa có lịch họp trên máy chủ.';$('savedList').append(p);}
  for(const item of schedules){
   const row=document.createElement('article'),info=document.createElement('div'),title=document.createElement('strong'),detail=document.createElement('p'),button=document.createElement('button');row.className='saved-item';title.textContent=item.title;detail.textContent=new Date(item.startTime).toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh'})+' · '+(rooms.find(r=>r.id===item.room)?.name||item.room)+(item.recurrenceSeriesId?' · Chuỗi #'+item.recurrenceSeriesId:'');button.type='button';button.className='button secondary';button.textContent='Hủy lịch';
   button.addEventListener('click',async()=>{if(busy||!confirm('Hủy lần họp này? Các lần khác được giữ lại.'))return;button.disabled=true;try{await api('/'+item.id,{method:'DELETE'});schedules=schedules.filter(s=>s.id!==item.id);render();status('Đã hủy lịch trên máy chủ.');}catch(e){button.disabled=false;status(e.message,true);}});
   info.append(title,detail);row.append(info,button);$('savedList').append(row);
  }
 }
 form.querySelectorAll('[name=frequencyChoice]').forEach(radio=>radio.addEventListener('change',()=>{$('repeatFrequency').value=radio.value;summary();}));
 $('repeatStart').addEventListener('input',()=>{$('meetingDate').value=$('repeatStart').value;summary();});
 form.addEventListener('input',summary);form.addEventListener('change',summary);
 $('resetBtn').addEventListener('click',()=>{if(!busy&&confirm('Xóa thông tin đang nhập?')){form.reset();$('repeatFrequency').value='NONE';summary();fields.forEach(id=>$(id+'Error').textContent='');}});
 form.addEventListener('submit',async event=>{
 event.preventDefault();if(busy||!ready)return;
 const p=payload();if(!validate(p)){status('Kiểm tra thông tin được đánh dấu.',true);return;}
 const recurring=!!p.recurrence;
 const request=recurring?{roomId:p.room,title:p.title,description:p.notes,startTime:p.startTime,endTime:p.endTime,participants:p.emails,frequency:p.recurrence.frequency,interval:p.recurrence.interval,occurrences:p.recurrence.until?null:p.recurrence.count,weekdays:p.recurrence.weekdays,monthDay:p.recurrence.monthDay,until:p.recurrence.until}:{title:p.title,description:p.notes,startTime:p.startTime,endTime:p.endTime,participants:p.emails,room:p.room,recurrence:'NONE',repeatCount:0};
 lock(true);status('Đang tạo lịch trên máy chủ…');
 try{
 const created=await api(recurring?'/recurring':'',{method:'POST',body:JSON.stringify(request)});
 if(!created||(recurring?created.seriesId==null:created.id==null))throw Error('API không trả mã lịch vừa tạo.');
 const createdId=recurring?created.seriesId:created.id;
 status(recurring?'Đã tạo '+created.occurrences+' cuộc họp trong chuỗi #'+createdId+'.':'Đã tạo cuộc họp #'+createdId+'.');
 try{schedules=await api();render();status('Đã lưu lịch vào backend và tải lại danh sách.');}catch(e){status('Lịch #'+createdId+' đã được tạo, nhưng chưa tải lại được danh sách. Tải lại trang trước khi tạo tiếp.',true);}
 }catch(e){status(e.message,true);}finally{lock(false);}
});
 async function load(){$('submitBtn').disabled=true;status('Đang tải phòng và lịch từ máy chủ…');try{const data=await Promise.all([api('/rooms'),api()]);rooms=data[0].map(r=>({id:r.roomId,name:r.roomName,capacity:r.capacity}));schedules=data[1].filter(m=>m.status!=='CANCELLED');if(!Array.isArray(rooms)||!Array.isArray(schedules))throw Error('Dữ liệu API không hợp lệ.');$('meetingRoom').replaceChildren();const empty=document.createElement('option');empty.value='';empty.textContent='Chọn phòng họp';$('meetingRoom').append(empty);for(const room of rooms){const option=document.createElement('option');option.value=room.id;option.textContent=room.name+' · '+room.capacity+' người';$('meetingRoom').append(option);}ready=rooms.length>0;render();status(rooms.length?'Đã tải lịch từ máy chủ.':'Chưa có phòng trong cơ sở dữ liệu. Thêm phòng ở màn hình quản lý phòng trước khi tạo lịch.',!rooms.length);}catch(e){status(e.message+' Tải lại trang để thử lại.',true);}finally{lock(false);summary();}}

 load();
})();
