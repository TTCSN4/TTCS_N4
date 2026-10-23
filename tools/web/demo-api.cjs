'use strict';
const {randomUUID}=require('node:crypto');
const {stamp, range, validDate, localTime}=require('./domain-rules.cjs');
const policy=require('./access-policy.cjs');
const copy=x=>structuredClone(x),day=n=>new Date(Date.now()+n*86400000+7*3600000).toISOString().slice(0,10);
let revision=1;
let rooms=['A101','A102','B201','B202','C301','C302','D401','D402'].map((id,i)=>({roomId:id,roomName:'Phòng '+id,capacity:[10,20,15,30,12,25,40,50][i],location:'Tòa '+id[0]+' - Tầng '+id[1],status:i===3?'MAINTENANCE':i===5?'INACTIVE':'AVAILABLE',qrCode:'DEMO:ROOM:'+id}));
let equipment=rooms.flatMap(r=>['Máy chiếu','Micro không dây','Laptop','Camera họp','TV','Bảng trắng'].map((name,i)=>({equipmentId:r.roomId+'-EQ'+(i+1),equipmentName:name,roomId:r.roomId,room:r.roomId,status:'AVAILABLE',totalQuantity:[3,8,4,2,2,3][i],type:['PROJECTOR','MICROPHONE','LAPTOP','CAMERA','TV','BOARD'][i]})));
const roles=['ADMIN','PM','MEMBER'].map(id=>({id,roleId:id,name:id,roleName:id}));
const departments=[{id:'IT',name:'Kỹ thuật'},{id:'HR',name:'Nhân sự'},{id:'SALES',name:'Kinh doanh'}];
let users=['Nguyễn Văn A','Trần Thị B','Lê Đức C','Phạm Thu D','Hoàng Văn E','Bùi Thị F'].map((fullName,i)=>({id:'DEMO0'+(i+1),userId:'DEMO0'+(i+1),username:'demo0'+(i+1),fullName,email:'demo0'+(i+1)+'@example.com',phone:'090000000'+i,departmentId:departments[i%3].id,department:departments[i%3].name,roleId:roles[i%3].id,roles:[roles[i%3].id],status:'ACTIVE',createdAt:new Date().toISOString(),lastLogin:null}));
let meetings=Array.from({length:18},(_,i)=>{const date=day(i<12?i-12:i-10),r=rooms.filter(r=>r.status==='AVAILABLE')[i%6];return {meetingId:'M'+String(i+1).padStart(2,'0'),title:['Họp kế hoạch tuần','Đánh giá tiến độ','Trao đổi dự án'][i%3],organizerId:'DEMO01',roomId:r.roomId,description:'Cuộc họp minh họa của hệ thống demo.',startTime:date+'T09:00:00',endTime:date+'T10:00:00',participants:['DEMO01','DEMO02','DEMO03'],equipmentIds:i%4===0?[r.roomId+'-EQ1']:[],status:i%7===0?'CANCELLED':i<12?'COMPLETED':'SCHEDULED',isRecurring:false,cancelReason:i%7===0?'Thay đổi kế hoạch':''}});
let equipmentBookings=meetings.filter(m=>m.status!=='CANCELLED').flatMap(m=>m.equipmentIds.map(equipmentId=>({id:m.meetingId+':'+equipmentId,meetingId:m.meetingId,equipmentId,quantity:1,startTime:m.startTime,endTime:m.endTime,status:'BOOKED'})));
let attendance=[],hrmHistory=[],reminders=[],roomGrants=rooms.map(r=>({id:r.roomId,name:r.roomName,location:r.location,capacity:r.capacity,roles:['ADMIN','USER','GUEST'],status:'open',version:1,updatedAt:'Demo'})),extra={};
require('./demo-seed.cjs')({rooms,equipment,users,meetings,equipmentBookings,attendance,hrmHistory,reminders,roomGrants,extra});
let actor={...users[0],role:'ADMIN',authorities:['ROLE_ADMIN']};
const overlap=(a,b,c,d)=>stamp(a)<stamp(d)&&stamp(c)<stamp(b);
function fail(message,status=400){const e=Error(message);e.status=status;throw e}
function need(list,id,key){return list.find(x=>String(x[key])===String(id))||fail('Không tìm thấy dữ liệu demo.',404)}
const activeRoom=r=>['ACTIVE','AVAILABLE'].includes(r.status);
function free(r,start,end,ignore){return activeRoom(r)&&!meetings.some(m=>m.meetingId!==ignore&&m.roomId===r.roomId&&m.status!=='CANCELLED'&&overlap(m.startTime,m.endTime,start,end))}
function booked(id,start,end,ignore,ignoreMeetingId){const events=[],cancelled=new Set(meetings.filter(m=>m.status==='CANCELLED').map(m=>m.meetingId));for(const b of equipmentBookings){if(b.id===ignore||ignoreMeetingId&&b.meetingId===ignoreMeetingId||b.equipmentId!==id||b.status==='CANCELLED'||cancelled.has(b.meetingId)||!overlap(b.startTime,b.endTime,start,end))continue;events.push([Math.max(stamp(start),stamp(b.startTime)),b.quantity],[Math.min(stamp(end),stamp(b.endTime)),-b.quantity])}events.sort((a,b)=>a[0]-b[0]||a[1]-b[1]);let n=0,max=0;for(const [,delta]of events){n+=delta;max=Math.max(max,n)}return max}
function roomDto(r,start,end){return {...r,id:r.roomId,name:r.roomName,isAvailable:start&&end?free(r,start,end):activeRoom(r),available:start&&end?free(r,start,end):activeRoom(r)}}
function meetingDto(m){return {...m,id:m.meetingId,room:m.roomId,roomName:rooms.find(r=>r.roomId===m.roomId)?.roomName||m.roomId,organizerName:users.find(u=>u.id===m.organizerId)?.fullName||m.organizerId,participants:[...m.participants],equipment:equipmentBookings.filter(b=>b.meetingId===m.meetingId&&b.status!=='CANCELLED').map(b=>({equipmentId:b.equipmentId,quantity:b.quantity})),equipmentIds:equipmentBookings.filter(b=>b.meetingId===m.meetingId&&b.status!=='CANCELLED').map(b=>b.equipmentId)}}
function expandParticipants(value){
 for(const key of ['participants','groupIds','participantGroups'])if(value[key]!==undefined&&!Array.isArray(value[key]))fail('Danh sách người/nhóm tham dự phải là mảng.');
 const raw=Array.isArray(value.participants)?value.participants:[];
 const groups=[...(value.groupIds||[]),...(value.participantGroups||[])].map(String);
 const members=[];
 for(const groupId of groups){
  const group=(extra.participantGroups||[]).find(g=>String(g.id)===groupId)||departments.map(d=>({id:'department:'+d.id,memberIds:users.filter(u=>u.departmentId===d.id).map(u=>u.id)})).find(g=>g.id===groupId);
  if(!group) fail('Không tìm thấy nhóm người tham dự: '+groupId,404);
  members.push(...(group.memberIds||[]).filter(id=>users.some(u=>u.id===id&&u.status==='ACTIVE')));
 }
 value.participants=[...new Set([...raw,...members].map(x=>{
  const text=String(x); return users.find(u=>u.email.toLowerCase()===text.toLowerCase()||u.fullName.toLowerCase()===text.toLowerCase())?.id||text;
 }))];
 return value;
}
function checkMeeting(value,ignore){
 const r=need(rooms,value.roomId||value.room,'roomId');
 if(typeof value.title!=='string'||!value.title.trim()||value.title.length>255||!value.organizerId)fail('Điền tên và chủ trì hợp lệ.');
 range(value.startTime,value.endTime);
 const organizer=need(users,value.organizerId,'id');
 if(organizer.status!=='ACTIVE')fail('Chủ trì không còn hoạt động.',409);
 policy.assertRoomPermission({roomGrants},organizer,r.roomId);
 expandParticipants(value);
 const ids=[...new Set([value.organizerId,...value.participants].map(String))];
 ids.forEach(id=>{if(need(users,id,'id').status!=='ACTIVE')fail('Người tham dự không còn hoạt động.',409)});
 const previous=ignore?meetings.find(m=>m.meetingId===ignore):null,guests=previous?Math.max(0,(previous.participantCount||previous.participants.length)-previous.participants.length):0;
 const headcount=value.participantCount??(ids.length+guests);
 if(!Number.isSafeInteger(headcount)||headcount<ids.length)fail('Tổng số người không hợp lệ.');
 if(headcount>r.capacity)fail('Số người tham dự vượt sức chứa phòng.',409);
 if(!free(r,value.startTime,value.endTime,ignore))fail('Phòng không hoạt động hoặc đã có lịch trùng.',409);
 if(calendarBusy(ids,value.startTime,value.endTime).some(m=>m.meetingId!==ignore))fail('Chủ trì hoặc người tham dự đã có lịch trùng.',409);
 return {r,ids,headcount};
}
function saveMeeting(value,id){
 const old=id?need(meetings,id,'meetingId'):null;
 const explicitPeople=value.participants!==undefined,selectedGroups=value.participantGroups??value.groupIds,requestedCount=value.participantCount;
 const storedGroups=selectedGroups??(explicitPeople?[]:old?.participantGroups||[]);
 if(old&&old.status!=='SCHEDULED')fail('Chỉ được sửa lịch đang chờ diễn ra.',409);
 if(value.status!==undefined&&value.status!=='SCHEDULED')fail('Dùng chức năng chuyển trạng thái hoặc hủy lịch.',409);
 if(old)value={...old,...value,participants:value.participants??old.participants,participantGroups:selectedGroups||[],groupIds:[],participantCount:requestedCount};
 if(old&&value.participants===undefined&&value.groupIds===undefined&&value.participantGroups===undefined)value={...value,participants:[...old.participants]};
 if(!Number.isFinite(stamp(value.startTime))||!Number.isFinite(stamp(value.endTime)))fail('Ngày giờ không hợp lệ.');
 value={...value,organizerId:value.organizerId||old?.organizerId||actor.id,startTime:new Date(stamp(value.startTime)+7*3600000).toISOString().slice(0,19),endTime:new Date(stamp(value.endTime)+7*3600000).toISOString().slice(0,19)};
 const {r,ids,headcount}=checkMeeting(value,id);
 if(stamp(value.startTime)<=Date.now())fail('Chọn thời gian bắt đầu trong tương lai.');
 const existingBookings=old?equipmentBookings.filter(b=>b.meetingId===id&&b.status!=='CANCELLED'):[];
 if(value.equipment!==undefined&&(!Array.isArray(value.equipment)||value.equipment.some(e=>!e||typeof e.equipmentId!=='string'||!Number.isSafeInteger(e.quantity)||e.quantity<1)||new Set(value.equipment.map(e=>e.equipmentId)).size!==value.equipment.length))fail('Thiết bị và số lượng không hợp lệ.');
 if(value.equipmentIds!==undefined&&!Array.isArray(value.equipmentIds))fail('Danh sách thiết bị không hợp lệ.');
 const equipmentRows=value.equipment!==undefined?value.equipment:[...new Set(value.equipmentIds||existingBookings.map(b=>b.equipmentId))].map(equipmentId=>({equipmentId,quantity:existingBookings.find(b=>b.equipmentId===equipmentId)?.quantity||1}));
 const equipmentIds=equipmentRows.map(e=>e.equipmentId);const quantities=new Map(equipmentRows.map(e=>[e.equipmentId,e.quantity]));
 for(const eqId of equipmentIds){const e=need(equipment,eqId,'equipmentId');const prior=existingBookings.find(b=>b.equipmentId===eqId);if(e.roomId!==r.roomId||e.status!=='AVAILABLE'||booked(eqId,value.startTime,value.endTime,prior?.id)+(quantities.get(eqId)||1)>(e.totalQuantity-(e.unavailableQuantity||0)))fail('Thiết bị đã hết hoặc không thuộc phòng.',409)}
 const m={...old,meetingId:id||'M-'+randomUUID(),title:value.title.trim(),description:typeof value.description==='string'?value.description:'',organizerId:value.organizerId,startTime:value.startTime,endTime:value.endTime,roomId:r.roomId,participants:ids,participantCount:headcount,participantGroups:[...new Set(storedGroups)],equipmentIds,status:old?.status||'SCHEDULED',isRecurring:!!value.isRecurring,recurrenceSeriesId:value.recurrenceSeriesId||old?.recurrenceSeriesId||null};if(old)Object.assign(old,m);else meetings.push(m);
 // A booking ID is a stable key. Replace rows instead of retaining duplicate cancelled rows.
 equipmentBookings=equipmentBookings.filter(b=>b.meetingId!==m.meetingId);
 for(const row of equipmentRows){const booking={id:m.meetingId+':'+row.equipmentId,meetingId:m.meetingId,equipmentId:row.equipmentId,quantity:row.quantity,startTime:m.startTime,endTime:m.endTime,status:'BOOKED'};equipmentBookings.push(booking)}
 return meetingDto(m)
}function legacy(){return {...extra,rooms:rooms.map(r=>({id:r.roomId,name:r.roomName,cap:r.capacity,floor:r.location,eq:equipment.filter(e=>e.roomId===r.roomId&&e.status==='AVAILABLE').map(e=>e.equipmentName)})),people:users.map(u=>({id:u.id,username:u.username,name:u.fullName,email:u.email,dept:u.department,role:u.roles.includes('ADMIN')?'Admin':u.roles.includes('PM')?'Người đặt lịch':'Người tham dự',status:u.status==='ACTIVE'?'Hoạt động':'Tạm khóa'})),bookings:meetings.map(m=>({id:m.meetingId,title:m.title,description:m.description||'',date:m.startTime.slice(0,10),start:m.startTime.slice(11,16),end:m.endTime.slice(11,16),room:m.roomId,organizerId:m.organizerId,participantIds:m.participants,people:m.participantCount||m.participants.length,status:m.status==='CANCELLED'?'cancelled':'confirmed',meetingStatus:m.status,cancellationReason:m.cancelReason||'',equipment:equipmentBookings.filter(b=>b.meetingId===m.meetingId&&b.status!=='CANCELLED').map(b=>({id:b.equipmentId,quantity:b.quantity}))})),equipment:equipment.map(e=>({id:e.equipmentId,name:e.equipmentName,category:e.type,location:e.roomId,total:e.totalQuantity,available:e.status==='AVAILABLE'?Math.max(0,e.totalQuantity-(e.unavailableQuantity||0)-booked(e.equipmentId,new Date().toISOString(),new Date(Date.now()+1).toISOString())):0,status:e.status==='AVAILABLE'?'ready':e.status==='MAINTENANCE'?'maintenance':'inactive',booked:equipmentBookings.some(b=>b.equipmentId===e.equipmentId&&b.status!=='CANCELLED'&&stamp(b.endTime)>Date.now())}))}}
function snapshot(internal=false){const result=copy({mode:'demo',revision,actor,rooms:rooms.map(r=>roomDto(r)),meetings:meetings.map(meetingDto),equipment,users,roles,departments,equipmentBookings,attendance,hrmHistory,reminders,roomGrants,legacy:legacy(),extra});if(!internal){result.policyVersion=JSON.stringify(extra.permissions||null);result.allowedModules=Array.from({length:28},(_,i)=>'us'+String(i+1).padStart(2,'0')).filter(id=>policy.canModule(actor.role,id,{extra}));delete result.extra.authCredentials;delete result.legacy.authCredentials;if(actor?.role==='MEMBER'){result.users=result.users.map(u=>({id:u.id,userId:u.id,fullName:u.fullName}));result.equipment=[];result.equipmentBookings=[];result.roomGrants=[];result.roles=[];result.departments=[];result.hrmHistory=[];result.reminders=[];result.attendance=result.attendance.filter(a=>a.userId===actor.id);result.extra={};result.meetings=policy.can({extra},actor.role,0)?result.meetings.filter(m=>policy.canReadMeeting(actor,m)):[];result.legacy={bookings:policy.can({extra},actor.role,0)?result.legacy.bookings.filter(m=>m.organizerId===actor.id||m.participantIds.includes(actor.id)):[]};}}return result;}
function validateUsers(next){
 if(!Array.isArray(next)||!next.some(u=>u.status==='ACTIVE'&&u.roles?.includes('ADMIN')))fail('Phải giữ ít nhất một Admin hoạt động.',409);
 const ids=new Set(),emails=new Set(),names=new Set();
 for(const user of next){
  user.email=String(user.email||'').trim().toLowerCase();user.username=String(user.username||user.email.split('@')[0]).trim().toLowerCase();
  if(!user.id||!user.fullName?.trim()||!/^\S+@\S+\.\S+$/.test(user.email)||!Array.isArray(user.roles)||!user.roles.length||user.roles.some(r=>!roles.some(x=>x.id===r))||!['ACTIVE','LOCKED'].includes(user.status))fail('Thông tin tài khoản không hợp lệ.');
  if(ids.has(user.id)||emails.has(user.email)||names.has(user.username))fail('ID, email hoặc tên đăng nhập bị trùng.',409);
  ids.add(user.id);emails.add(user.email);names.add(user.username);
 }
 for(const old of users)if(!ids.has(old.id)&&meetings.some(m=>m.organizerId===old.id||m.participants.includes(old.id)))fail('Tài khoản có lịch sử cuộc họp; hãy khóa thay vì xóa.',409);
 return next;
}
function legacyWrite(key,value){
 if(key==='permissions'){
  if(!Array.isArray(value)||value.length!==3||value.some(row=>!Array.isArray(row)||row.length!==6||row.some(x=>typeof x!=='boolean'))||value[0].some(x=>!x))fail('Ma trận quyền phải gồm 3 vai trò, 6 quyền và giữ quyền Admin.');
  extra.permissions=copy(value);return;
 }
 if(key==='bookings'){
 if(!Array.isArray(value)||value.some(b=>!b||!(typeof b.id==='string'&&b.id||Number.isSafeInteger(b.id))))fail('Danh sách lịch không hợp lệ.');
 value=value.map(b=>({...b,id:String(b.id)}));if(new Set(value.map(b=>b.id)).size!==value.length)fail('ID lịch bị trùng.');
  const previous=legacy().bookings;
  for(const b of value){
   const old=meetings.find(m=>m.meetingId===b.id),view=previous.find(m=>m.id===b.id);
   if(view&&Object.keys(view).every(key=>JSON.stringify(view[key])===JSON.stringify(b[key])))continue;
   if(b.status==='cancelled'){
    if(!old)fail('Không tìm thấy lịch cần hủy.',404);
    if(['COMPLETED','CANCELLED'].includes(old.status))fail('Lịch không còn cho phép hủy.',409);
    old.status='CANCELLED';old.cancelReason=String(b.cancellationReason||'');equipmentBookings.filter(x=>x.meetingId===old.meetingId).forEach(x=>x.status='CANCELLED');continue;
   }
   const people=Number(b.people),participants=b.participantIds||old?.participants||[b.organizerId||b.createdBy||actor.id];
   if(!Number.isSafeInteger(people)||people<new Set([b.organizerId||old?.organizerId||actor.id,...participants]).size||people>need(rooms,b.room,'roomId').capacity)fail('Số người tham dự vượt sức chứa hoặc không hợp lệ.',409);
   const saved=saveMeeting({title:b.title,description:b.description??old?.description??'',participantCount:people,organizerId:b.organizerId||b.createdBy||old?.organizerId||actor.id,roomId:b.room,startTime:b.date+'T'+b.start+':00',endTime:b.date+'T'+b.end+':00',participants,equipment:(b.equipment||[]).map(e=>({equipmentId:String(e.id),quantity:Number(e.quantity)}))},old?.meetingId);
   // Keep the client-generated key for legacy adapters creating a booking.
   if(!old){const created=need(meetings,saved.meetingId,'meetingId');equipmentBookings.filter(x=>x.meetingId===created.meetingId).forEach(x=>{x.meetingId=b.id;x.id=b.id+':'+x.equipmentId});created.meetingId=b.id;}
  }
  for(const old of meetings)if(!value.some(b=>b.id===old.meetingId)&&old.status!=='CANCELLED')fail('Không được xóa lịch sử bằng cách ghi đè danh sách. Dùng chức năng hủy.',409);
  return;
 }
 if(key==='people'){
  if(!Array.isArray(value))fail('Danh sách tài khoản không hợp lệ.');
  users=validateUsers(value.map(p=>{const old=users.find(u=>u.id===String(p.id)),role=p.role==='Admin'?'ADMIN':p.role==='Người đặt lịch'?'PM':'MEMBER';return {...old,id:String(p.id),userId:String(p.id),username:p.username||old?.username,fullName:p.name,email:p.email,department:p.dept,roles:[role],roleId:role,status:p.status==='Tạm khóa'?'LOCKED':'ACTIVE',createdAt:old?.createdAt||new Date().toISOString()}}));return;
 }
 if(key==='accounts'){
  if(!Array.isArray(value))fail('Danh sách tài khoản không hợp lệ.');
  users=validateUsers(value.map(u=>({...u,id:String(u.id),userId:String(u.id),roleId:u.roles?.includes('ADMIN')?'ADMIN':u.roles?.includes('PM')?'PM':'MEMBER'})));return;
 }
 if(key==='equipment'){
  if(!Array.isArray(value)||new Set(value.map(e=>e.id)).size!==value.length)fail('Danh mục thiết bị không hợp lệ.');
  const next=value.map(e=>{const roomId=rooms.find(r=>String(e.location).includes(r.roomId))?.roomId,old=equipment.find(x=>x.equipmentId===String(e.id));if(!roomId)fail('Phòng thiết bị không tồn tại.');const total=Number(e.total);if(!Number.isSafeInteger(total)||total<0||!String(e.name||'').trim())fail('Tên hoặc số lượng thiết bị không hợp lệ.');const status=e.status==='ready'?'AVAILABLE':e.status==='maintenance'?'MAINTENANCE':'INACTIVE';const unavailable=old?.unavailableQuantity||0;if(total<unavailable)fail('Tổng số lượng không được nhỏ hơn số bảo trì.',409);return {equipmentId:String(e.id),equipmentName:e.name,type:e.category||'OTHER',roomId,room:roomId,totalQuantity:total,unavailableQuantity:unavailable,status};});
  for(const old of equipment){const item=next.find(e=>e.equipmentId===old.equipmentId),peak=booked(old.equipmentId,new Date().toISOString(),'9999-12-31T23:59:59Z');if(peak&&(!item||item.roomId!==old.roomId||item.status!=='AVAILABLE'||item.totalQuantity-item.unavailableQuantity<peak))fail('Không được thay đổi thiết bị làm thiếu tài nguyên đã đặt.',409);}
  equipment=next;return;
 }
 if(key==='notifications'){
  if(!value||typeof value!=='object'||!['15 phút','30 phút','1 giờ','1 ngày'].includes(value.before)&&(!Number.isFinite(Number(value.before))||Number(value.before)<0))fail('Cấu hình nhắc lịch không hợp lệ.');
  reminders=meetings.filter(m=>m.status==='SCHEDULED').map(m=>({id:'REM-'+m.meetingId,meetingId:m.meetingId,channels:[value.app?'APP':null,value.email?'EMAIL_DEMO':null].filter(Boolean),before:value.before,status:'SCHEDULED',demo:true}));
 }
 extra[key]=copy(value);
}
function usage(from,to,roomId){if(!validDate(from)||!validDate(to)||from>to||(Date.parse(to)-Date.parse(from))/86400000>366)fail('Khoảng ngày báo cáo phải hợp lệ và không quá 367 ngày.');const rs=rooms.filter(r=>!roomId||r.roomId===roomId),daily=[];for(let t=Date.parse(from+'T00:00Z');t<=Date.parse(to+'T00:00Z')&&daily.length<367;t+=86400000){const date=new Date(t).toISOString().slice(0,10),ms=meetings.filter(m=>m.status!=='CANCELLED'&&rs.some(r=>r.roomId===m.roomId)&&m.startTime.slice(0,10)===date);daily.push({date,bookingCount:ms.length,usedHours:ms.reduce((n,m)=>n+(stamp(m.endTime)-stamp(m.startTime))/3600000,0),availableHours:rs.length*8})}const rows=rs.map(r=>{const ms=meetings.filter(m=>m.status!=='CANCELLED'&&m.roomId===r.roomId&&m.startTime.slice(0,10)>=from&&m.startTime.slice(0,10)<=to),usedHours=ms.reduce((n,m)=>n+(stamp(m.endTime)-stamp(m.startTime))/3600000,0),availableHours=daily.length*8;return {roomId:r.roomId,roomName:r.roomName,bookingCount:ms.length,usedHours,availableHours,utilizationPercent:availableHours?usedHours/availableHours*100:0}}),usedHours=rows.reduce((n,r)=>n+r.usedHours,0),availableHours=daily.length*rs.length*8;return {from,to,calculationBasis:'Demo: 8 giờ/phòng/ngày; không tính lịch đã hủy.',bookingCount:rows.reduce((n,r)=>n+r.bookingCount,0),usedHours,availableHours,utilizationPercent:availableHours?usedHours/availableHours*100:0,rooms:rows,daily}}
async function body(req){let text='';for await(const b of req){text+=b;if(Buffer.byteLength(text)>1000000)fail('Yêu cầu quá lớn.',413)}if(!text)return {};let value;try{value=JSON.parse(text)}catch{fail('JSON không hợp lệ.')}if(!value||typeof value!=='object'||Array.isArray(value))fail('Nội dung JSON phải là object.');return value;}
function calendarPeople(b){const raw=[...(b.participantIds||b.participants||[]),...(b.groupIds||b.participantGroups||[])];if(!Array.isArray(raw)||!raw.length)fail('Chọn ít nhất một tài khoản hoặc nhóm hợp lệ.');const expanded=[];for(const item of raw){const group=(extra.participantGroups||[]).find(g=>String(g.id)===String(item))||departments.find(d=>'department:'+d.id===String(item));if(group)expanded.push(...(group.memberIds||users.filter(u=>u.departmentId===group.id).map(u=>u.id)));else expanded.push(item)}const ids=[...new Set(expanded)];for(const id of ids){const u=need(users,id,'id');if(u.status!=='ACTIVE')fail('Tài khoản '+u.fullName+' không còn hoạt động.');}return ids;}
function calendarDate(value){const date=String(value||'');if(!/^\d{4}-\d{2}-\d{2}$/.test(date)||!Number.isFinite(stamp(date+'T00:00:00'))||new Date(stamp(date+'T00:00:00')+7*3600000).toISOString().slice(0,10)!==date)fail('Ngày kiểm tra không hợp lệ.');return date;}
function calendarBusy(ids,start,end){return meetings.filter(m=>m.status!=='CANCELLED'&&ids.some(id=>m.organizerId===id||m.participants.includes(id)&&m.participantStatuses?.[id]!=='DECLINED')&&overlap(m.startTime,m.endTime,start,end));}
function calendarSlots(date,duration,ids){if(!Number.isSafeInteger(duration)||duration<15||duration>480)fail('Thời lượng phải là số nguyên từ 15 đến 480 phút.');const clock=n=>String(Math.floor(n/60)).padStart(2,'0')+':'+String(n%60).padStart(2,'0');const slots=[];for(let minute=480;minute+duration<=1080;minute+=30){const startTime=date+'T'+clock(minute)+':00',endTime=date+'T'+clock(minute+duration)+':00';if(stamp(startTime)>Date.now()&&!calendarBusy(ids,startTime,endTime).length)slots.push({startTime,endTime});}return slots;}
function calendarConflicts(b){const date=calendarDate(b.date),ids=calendarPeople(b);if(!/^([01]\d|2[0-3]):[0-5]\d$/.test(b.startTime||'')||!/^([01]\d|2[0-3]):[0-5]\d$/.test(b.endTime||''))fail('Nhập giờ bắt đầu và kết thúc hợp lệ.');const startTime=date+'T'+b.startTime+':00',endTime=date+'T'+b.endTime+':00';if(stamp(endTime)<=stamp(startTime))fail('Giờ kết thúc phải sau giờ bắt đầu.');const busy=calendarBusy(ids,startTime,endTime),people=ids.map(id=>{const u=need(users,id,'id');return {id,fullName:u.fullName,department:u.department,conflicts:busy.filter(m=>m.organizerId===id||m.participants.includes(id)).sort((a,b)=>stamp(a.startTime)-stamp(b.startTime)).map(m=>{const room=rooms.find(r=>r.roomId===m.roomId);return{meetingId:m.meetingId,title:m.title,startTime:m.startTime,endTime:m.endTime,roomId:m.roomId,roomName:room?.roomName||m.roomId,location:room?.location||'Chưa cập nhật vị trí',status:m.status};})};});return{date,startTime,endTime,people,busyCount:people.filter(p=>p.conflicts.length).length,slots:calendarSlots(date,Number(b.durationMinutes||60),ids),revision};}
async function handler(req,res,u){let before;const method=req.method,q=u.searchParams,send=(status,value)=>{res.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-TTCS-Mode':'demo'});res.end(JSON.stringify(value))};try{let p;try{p=decodeURIComponent(u.pathname)}catch{fail('Đường dẫn không hợp lệ.')}
if(handler.auth){if(await handler.auth.handle(req,res,u,handler))return;handler.auth.authorize(req,u,handler);}
if(p==='/__portal/health'){send(200,{reachable:true,mode:'demo',revision});return}
if(p==='/api/demo/revision'&&method==='GET'){send(200,{revision,role:actor.role,userId:actor.id,policyVersion:JSON.stringify(extra.permissions||null)});return}
if(p==='/api/demo/state'&&method==='GET'){send(200,snapshot());return}
if(p==='/api/demo/legacy'&&method==='POST'){const b=await body(req);if(handler.auth)handler.auth.authorizeBody(req,u,b,handler);before=handler.exportState();if(['bookings','people','accounts','equipment'].includes(b.key)&&b.expectedRevision!==undefined&&b.expectedRevision!==revision)fail('Dữ liệu đã thay đổi. Tải lại trước khi lưu danh sách.',409);legacyWrite(b.key,b.value);revision++;send(200,{ok:true,revision});return}
if(method==='GET'){
if(p==='/api/rooms'||p==='/api/rooms/available'||p==='/api/rooms/availability'){const start=q.get('startTime'),end=q.get('endTime'),cap=Number(q.get('minCapacity')||q.get('participants')||0);if(start||end)range(start,end);if(!Number.isSafeInteger(cap)||cap<0)fail('Sức chứa phải là số nguyên không âm.');send(200,rooms.filter(r=>r.capacity>=cap).filter(r=>!start||free(r,start,end)).map(r=>roomDto(r,start,end)));return}
if(/^\/api\/rooms\/[^/]+$/.test(p)){send(200,roomDto(need(rooms,decodeURIComponent(p.split('/').pop()),'roomId')));return}
if(p==='/api/meetings'||p==='/api/meetings/history'||p==='/api/mobile/calendar'){send(200,meetings.filter(m=>policy.canReadMeeting(actor,m)&&(p!=='/api/mobile/calendar'||m.organizerId===actor.id||m.participants.includes(actor.id))).filter(m=>p.endsWith('/history')?stamp(m.endTime)<Date.now():true).map(meetingDto));return}
if(/^\/api\/users\/[^/]+\/meeting-history/.test(p)){const parts=p.split('/'),id=parts[3],filtered=meetings.filter(m=>m.organizerId===id||m.participants.includes(id)).map(meetingDto);send(200,parts[5]?filtered.find(m=>m.meetingId===parts[5])||fail('Không có lịch mẫu.',404):filtered);return}
if(/^\/api\/meetings\/[^/]+\/participants$/.test(p)){send(200,need(meetings,p.split('/')[3],'meetingId').participants.map(id=>({...need(users,id,'id'),status:need(meetings,p.split('/')[3],'meetingId').participantStatuses?.[id]||'ACCEPTED',attendanceStatus:'INVITED'})));return}
if(/^\/api\/meetings\/[^/]+\/reminders$/.test(p)){send(200,reminders.filter(r=>r.meetingId===p.split('/')[3]));return}
if(/^\/api\/meetings\/[^/]+\/check-in-logs$/.test(p)){send(200,attendance.filter(a=>a.meetingId===p.split('/')[3]&&(actor.role!=='MEMBER'||a.userId===actor.id)));return}
if(/^\/api\/meetings\/[^/]+$/.test(p)){send(200,meetingDto(need(meetings,p.split('/')[3],'meetingId')));return}
if(p==='/api/equipment'){send(200,equipment);return}
if(p==='/api/equipment/bookings'){send(200,equipmentBookings);return}
if(p==='/api/equipment/status'){const at=q.get('at')||new Date().toISOString(),room=q.get('room');if(!Number.isFinite(stamp(at)))fail('Thời gian không hợp lệ.');const end=q.get('endTime')||new Date(stamp(at)+1).toISOString();range(at,end);send(200,equipment.filter(e=>!room||e.roomId===room).map(e=>{const used=booked(e.equipmentId,at,end,null,q.get('excludeMeetingId'));return {...e,room:e.roomId,bookedQuantity:used,maintenanceQuantity:e.status==='AVAILABLE'?(e.unavailableQuantity||0):e.totalQuantity,availableQuantity:e.status==='AVAILABLE'?Math.max(0,e.totalQuantity-used-(e.unavailableQuantity||0)):0}}));return}
if(/^\/api\/equipment\/[^/]+\/(availability|booked)$/.test(p)){range(q.get('startTime'),q.get('endTime'));const e=need(equipment,p.split('/')[3],'equipmentId'),used=booked(e.equipmentId,q.get('startTime'),q.get('endTime'));send(200,p.endsWith('/booked')?used:{equipmentId:e.equipmentId,totalQuantity:e.totalQuantity,bookedQuantity:used,availableQuantity:e.status==='AVAILABLE'?Math.max(0,e.totalQuantity-(e.unavailableQuantity||0)-used):0});return}
if(/^\/api\/equipment\/[^/]+$/.test(p)){send(200,need(equipment,p.split('/')[3],'equipmentId'));return}
if(p==='/api/participant-groups'){const active=new Set(users.filter(u=>u.status==='ACTIVE').map(u=>u.id));const groups=[...departments.map(d=>({id:'department:'+d.id,name:'Phòng ban · '+d.name,kind:'DEPARTMENT',memberIds:users.filter(u=>u.departmentId===d.id&&active.has(u.id)).map(u=>u.id)})),...(extra.participantGroups||[]).map(g=>({...g,kind:'CUSTOM',memberIds:g.memberIds.filter(id=>active.has(id))}))];send(200,groups);return}
if(p==='/api/users'){send(200,users);return}if(p==='/api/roles'||p==='/api/admin/roles'){send(200,roles);return}if(p==='/api/departments'){send(200,departments);return}if(p==='/api/participants'){send(200,{participants:users.map(u=>({id:u.id,name:u.fullName,email:u.email}))});return}
if(p==='/api/room-permissions'){send(200,{rooms:roomGrants,history:extra.roomPermissionHistory||[]});return}
if(p==='/api/admin/reports/room-usage'){send(200,usage(q.get('from'),q.get('to'),q.get('roomId')));return}
if(p==='/api/integrations/hrm/history'){send(200,hrmHistory);return}if(p==='/api/integrations/hrm/status'){send(200,{connected:true,status:'DEMO',lastSyncAt:hrmHistory[0]?.start||null});return}if(p==='/api/integrations/hrm/users'){send(200,users);return}if(p==='/api/integrations/hrm/departments'){send(200,departments);return}
if(p==='/api/demo/attendance'){send(200,attendance);return}
}
const b=await body(req);if(handler.auth)handler.auth.authorizeBody(req,u,b,handler);before=handler.exportState();let data,status=200;
if(p==='/api/availability/conflicts'&&method==='POST'){send(200,calendarConflicts(b));return}
if(p==='/api/demo/meeting-status'&&method==='POST'){const m=need(meetings,b.meetingId,'meetingId'),transitions={SCHEDULED:['ONGOING','CANCELLED'],ONGOING:['PAUSED','COMPLETED','CANCELLED'],PAUSED:['ONGOING','COMPLETED','CANCELLED'],COMPLETED:[],CANCELLED:[]};const next=String(b.status).toUpperCase();if(!(transitions[m.status]||[]).includes(next))fail('Không thể chuyển sang trạng thái này.',409);m.status=next;if(next==='CANCELLED')reminders.filter(x=>x.meetingId===m.meetingId).forEach(x=>x.status='CANCELLED');if(next==='CANCELLED')equipmentBookings.filter(x=>x.meetingId===m.meetingId).forEach(x=>x.status='CANCELLED');revision++;send(200,{status:next.toLowerCase(),time:new Date().toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh'}),actor:actor.fullName});return}

if(/^\/api\/equipment\/[^/]+\/(restore|maintenance)$/.test(p)&&method==='POST'){
if(req.actor?.role!=='ADMIN')fail('Chỉ Admin được cập nhật bảo trì thiết bị.',403);
const item=need(equipment,p.split('/')[3],'equipmentId'),quantity=b.quantity,repairing=item.status==='MAINTENANCE'?item.totalQuantity:(item.unavailableQuantity||0);
if(!Number.isSafeInteger(quantity)||quantity<1)fail('Nhập số lượng nguyên lớn hơn 0.');
if(!['AVAILABLE','MAINTENANCE'].includes(item.status))fail('Thiết bị đã ngừng sử dụng.',409);
const completing=p.endsWith('/restore');
if(completing&&quantity>repairing)fail('Số lượng sửa xong vượt quá số đang bảo trì.',409);
if(!completing){const peak=booked(item.equipmentId,new Date().toISOString(),new Date(Date.now()+10*365*86400000).toISOString());if(quantity>item.totalQuantity-repairing-peak)fail('Không đủ thiết bị chưa đặt để đưa vào bảo trì. Các lịch cũ được giữ nguyên.',409);}
item.unavailableQuantity=completing?repairing-quantity:repairing+quantity;item.status=item.unavailableQuantity===item.totalQuantity?'MAINTENANCE':'AVAILABLE';item.maintenanceUpdatedAt=new Date().toISOString();item.maintenanceUpdatedBy=actor.id;data=item;
}
else if(/^\/api\/rooms\/[^/]+\/(status|restore)$/.test(p)&&method==='POST'){
if(req.actor?.role!=='ADMIN')fail('Chỉ Admin được cập nhật trạng thái phòng.',403);const item=need(rooms,p.split('/')[3],'roomId'),next=p.endsWith('/restore')?'AVAILABLE':b.status;if(!['AVAILABLE','ACTIVE','MAINTENANCE','CONSTRUCTION','INACTIVE'].includes(next))fail('Trạng thái phòng không hợp lệ.');item.status=next==='ACTIVE'?'AVAILABLE':next;item.statusUpdatedAt=new Date().toISOString();item.statusUpdatedBy=actor.id;data=roomDto(item);
}
else if(p==='/api/participant-groups'&&method==='POST'){const name=String(b.name||'').trim(),memberIds=Array.isArray(b.memberIds)?[...new Set(b.memberIds)]:[];if(!name||name.length>80||!memberIds.length||memberIds.length>users.length)fail('Nhập tên nhóm tối đa 80 ký tự và chọn ít nhất một thành viên.');for(const id of memberIds){const member=need(users,id,'id');if(member.status!=='ACTIVE')fail('Nhóm chỉ gồm tài khoản đang hoạt động.');}extra.participantGroups||=[];if(extra.participantGroups.some(g=>g.name.toLocaleLowerCase('vi')===name.toLocaleLowerCase('vi')))fail('Tên nhóm đã tồn tại.',409);data={id:'GROUP-'+randomUUID().slice(0,8),name,memberIds,kind:'CUSTOM',createdBy:actor.id,createdAt:new Date().toISOString()};extra.participantGroups.push(data);status=201}
else if(/^\/api\/participant-groups\/[^/]+$/.test(p)&&method==='DELETE'){const id=p.split('/')[3];if(!(extra.participantGroups||[]).some(g=>g.id===id))fail('Không có nhóm này.',404);extra.participantGroups=extra.participantGroups.filter(g=>g.id!==id);data={ok:true}}
else if((p==='/api/meetings/create'||p==='/api/meetings')&&method==='POST'){data=saveMeeting(b);status=201}
else if(p==='/api/meetings/recurring'&&method==='POST'){
 range(b.startTime,b.endTime);
 const interval=Number(b.interval??1),count=b.occurrences==null?null:Number(b.occurrences);
 const startDay=localTime(b.startTime).slice(0,10),first=new Date(startDay+'T00:00:00Z');
 const until=b.until?new Date(calendarDate(b.until)+'T00:00:00Z'):null;
 if(!['WEEKLY','MONTHLY'].includes(b.frequency)||!Number.isSafeInteger(interval)||interval<1||interval>52||count!==null&&(!Number.isSafeInteger(count)||count<1||count>100)||count===null&&!until||until&&until<first)fail('Quy tắc lặp không hợp lệ.');
 const weekday=first.getUTCDay()||7,weekdays=b.weekdays??[weekday];
 if(b.frequency==='WEEKLY'&&(!Array.isArray(weekdays)||!weekdays.length||weekdays.some(d=>!Number.isSafeInteger(d)||d<1||d>7)))fail('Thứ trong tuần không hợp lệ.');
 if(b.frequency==='MONTHLY'&&(!Number.isSafeInteger(Number(b.monthDay))||Number(b.monthDay)<1||Number(b.monthDay)>31))fail('Ngày lặp trong tháng phải từ 1 đến 31.');
 // Bounded scan: invalid or excessively distant rules cannot monopolize the server.
 const maxEnd=new Date(first);maxEnd.setUTCFullYear(first.getUTCFullYear()+100);
 if(until&&until>maxEnd)fail('Ngày kết thúc chuỗi không quá 100 năm.');
 const limit=until||maxEnd,dates=[];
 for(let cursor=new Date(first);cursor<=limit;cursor.setUTCDate(cursor.getUTCDate()+1)){
  const weeks=Math.floor(((cursor-first)/86400000+weekday-1)/7),months=(cursor.getUTCFullYear()-first.getUTCFullYear())*12+cursor.getUTCMonth()-first.getUTCMonth();
  if(b.frequency==='WEEKLY'?weeks%interval===0&&weekdays.includes(cursor.getUTCDay()||7):months%interval===0&&cursor.getUTCDate()===Number(b.monthDay)){
   dates.push(cursor.toISOString().slice(0,10));if(dates.length>100)fail('Chuỗi không quá 100 lịch.');if(count&&dates.length===count)break;
  }
 }
 if(!dates.length||count&&dates.length!==count)fail('Không đủ ngày lặp trong giới hạn đã chọn.');
 const seriesId='SERIES-'+randomUUID(),duration=stamp(b.endTime)-stamp(b.startTime),clock=localTime(b.startTime).slice(10);
 const result=dates.map(date=>{const startTime=date+clock;return saveMeeting({...b,startTime,endTime:localTime(new Date(stamp(startTime)+duration).toISOString()),isRecurring:true,recurrenceSeriesId:seriesId})});
 data={seriesId,occurrences:result.length,meetings:result};status=201;
}
else if(/^\/api\/meetings\/[^/]+$/.test(p)&&method==='PUT'){data=saveMeeting(b,p.split('/')[3])}
else if(/^\/api\/meetings\/[^/]+$/.test(p)&&method==='DELETE'){const m=need(meetings,p.split('/')[3],'meetingId');if(m.status==='COMPLETED')fail('Không được hủy cuộc họp đã hoàn tất.',409);m.status='CANCELLED';m.cancelReason=q.get('reason')||b.reason||'Hủy trong bản demo';reminders.filter(x=>x.meetingId===m.meetingId).forEach(x=>x.status='CANCELLED');equipmentBookings.filter(x=>x.meetingId===m.meetingId).forEach(x=>x.status='CANCELLED');data=meetingDto(m)}
else if(p==='/api/rooms'&&method==='POST'||/^\/api\/rooms\/[^/]+$/.test(p)&&method==='PUT'){const id=method==='PUT'?p.split('/')[3]:b.roomId;if(typeof id!=='string'||!id||id.length>50||typeof b.roomName!=='string'||!b.roomName.trim()||!Number.isSafeInteger(b.capacity)||b.capacity<1||!['AVAILABLE','ACTIVE','MAINTENANCE','CONSTRUCTION','INACTIVE','UNAVAILABLE'].includes(b.status))fail('Thông tin phòng không hợp lệ.');const old=rooms.find(r=>r.roomId===id);if(method==='POST'&&old)fail('Mã phòng đã tồn tại.',409);if(method==='PUT'&&!old)fail('Không có phòng này.',404);data={...old,...b,roomId:id};if(old&&b.capacity<old.capacity&&meetings.some(m=>m.roomId===id&&m.status!=='CANCELLED'&&stamp(m.endTime)>Date.now()&&Math.max(m.participantCount||0,new Set([m.organizerId,...m.participants]).size)>b.capacity))fail('Sức chứa mới không đủ cho lịch đã đặt.',409);if(old)Object.assign(old,data);else {rooms.push(data);roomGrants.push({id:data.roomId,name:data.roomName,location:data.location,capacity:data.capacity,roles:['ADMIN','USER','GUEST'],status:'open',version:1,updatedAt:'Demo'});}status=method==='POST'?201:200}
else if(/^\/api\/rooms\/[^/]+$/.test(p)&&method==='DELETE'){const id=p.split('/')[3];need(rooms,id,'roomId');if(meetings.some(m=>m.roomId===id)||equipment.some(e=>e.roomId===id))fail('Phòng còn lịch đặt, hãy ngừng sử dụng thay vì xóa.',409);rooms=rooms.filter(r=>r.roomId!==id);roomGrants=roomGrants.filter(r=>r.id!==id);data={ok:true}}
else if(p==='/api/equipment/bookings'&&method==='POST'){
 const m=need(meetings,b.meetingId,'meetingId'),e=need(equipment,b.equipmentId,'equipmentId'),quantity=Number(b.quantity),startTime=b.startTime||m.startTime,endTime=b.endTime||m.endTime;
 range(startTime,endTime);
 if(stamp(startTime)<stamp(m.startTime)||stamp(endTime)>stamp(m.endTime))fail('Khoảng đặt thiết bị phải nằm trong thời gian cuộc họp.');
 if(m.status!=='SCHEDULED'||e.roomId!==m.roomId||e.status!=='AVAILABLE'||!Number.isSafeInteger(quantity)||quantity<1)fail('Thiết bị hoặc số lượng đặt không hợp lệ.');
 if(booked(e.equipmentId,startTime,endTime)+quantity>e.totalQuantity-(e.unavailableQuantity||0))fail('Số lượng thiết bị còn lại không đủ.',409);
 data={id:m.meetingId+':'+e.equipmentId,meetingId:m.meetingId,equipmentId:e.equipmentId,quantity,startTime:localTime(startTime),endTime:localTime(endTime),status:'BOOKED'};
 if(equipmentBookings.some(x=>x.id===data.id&&x.status!=='CANCELLED'))fail('Thiết bị đã có lượt đặt cho cuộc họp này.',409);
 equipmentBookings=equipmentBookings.filter(x=>x.id!==data.id);equipmentBookings.push(data);status=201;
}
else if(/^\/api\/equipment\/bookings\/[^/]+$/.test(p)&&method==='PUT'){
 const booking=need(equipmentBookings,p.split('/')[4],'id'),e=need(equipment,booking.equipmentId,'equipmentId'),m=need(meetings,booking.meetingId,'meetingId'),quantity=Number(b.quantity);
 if(m.status!=='SCHEDULED'||booking.status==='CANCELLED'||e.status!=='AVAILABLE'||e.roomId!==m.roomId)fail('Lượt đặt hoặc thiết bị không còn khả dụng.',409);
 if(!Number.isSafeInteger(quantity)||quantity<1)fail('Số lượng không hợp lệ.');
 if(booked(e.equipmentId,booking.startTime,booking.endTime,booking.id)+quantity>e.totalQuantity-(e.unavailableQuantity||0))fail('Không đủ thiết bị sau khi trừ số đang bảo trì.',409);
 booking.quantity=quantity;data=booking;
}
else if(/^\/api\/equipment\/bookings\/[^/]+$/.test(p)&&method==='DELETE'){const x=need(equipmentBookings,p.split('/')[4],'id');x.status='CANCELLED';data={ok:true}}
else if(p==='/api/equipment'&&method==='POST'||/^\/api\/equipment\/[^/]+$/.test(p)&&method==='PUT'){
 const id=method==='PUT'?p.split('/')[3]:b.equipmentId||'EQ-'+randomUUID(),old=equipment.find(e=>e.equipmentId===id);
 if(method==='POST'&&old)fail('Mã thiết bị đã tồn tại.',409);if(method==='PUT'&&!old)fail('Không tìm thấy thiết bị.',404);
 if(typeof b.equipmentName!=='string'||!b.equipmentName.trim()||!Number.isSafeInteger(b.totalQuantity)||b.totalQuantity<0)fail('Tên và số lượng thiết bị không hợp lệ.');
 const roomId=b.roomId||old?.roomId;need(rooms,roomId,'roomId');
 const nextStatus=b.status||old?.status||'AVAILABLE';if(!['AVAILABLE','MAINTENANCE','INACTIVE'].includes(nextStatus))fail('Trạng thái thiết bị không hợp lệ.');
 const unavailable=b.unavailableQuantity??old?.unavailableQuantity??0;
 if(!Number.isSafeInteger(unavailable)||unavailable<0||unavailable>b.totalQuantity)fail('Số đang bảo trì không hợp lệ.');
 const hasBookings=equipmentBookings.some(x=>x.equipmentId===id&&x.status!=='CANCELLED'&&stamp(x.endTime)>Date.now()&&meetings.some(m=>m.meetingId===x.meetingId&&m.status!=='CANCELLED'));
 if(old&&hasBookings&&(roomId!==old.roomId||nextStatus!=='AVAILABLE'||b.totalQuantity-unavailable<booked(id,new Date().toISOString(),'9999-12-31T23:59:59Z')))fail('Thiết bị đang có lịch đặt; không được làm thiếu tài nguyên đã cam kết.',409);
 data={...old,equipmentId:id,equipmentName:b.equipmentName.trim(),roomId,room:roomId,type:b.type||old?.type||'OTHER',totalQuantity:b.totalQuantity,unavailableQuantity:unavailable,status:nextStatus};
 if(old)Object.assign(old,data);else equipment.push(data);status=method==='POST'?201:200;
}
else if(/^\/api\/equipment\/[^/]+$/.test(p)&&method==='DELETE'){const id=p.split('/')[3];if(equipmentBookings.some(x=>x.equipmentId===id&&x.status!=='CANCELLED'&&stamp(x.endTime)>Date.now()))fail('Thiết bị còn lịch đặt.',409);equipment=equipment.filter(e=>e.equipmentId!==id);data={ok:true}}
else if(p==='/api/users'&&method==='POST'||/^\/api\/users\/[^/]+$/.test(p)&&method==='PUT'){
 const id=method==='PUT'?p.split('/')[3]:'USR-'+randomUUID(),old=users.find(u=>u.id===id);
 if(method==='PUT'&&!old)fail('Không tìm thấy tài khoản.',404);
 const email=String(b.email||'').trim().toLowerCase(),username=String(b.username||old?.username||email.split('@')[0]).trim().toLowerCase();
 if(typeof b.fullName!=='string'||!b.fullName.trim()||!/^\S+@\S+\.\S+$/.test(email)||!username||username.length>80)fail('Họ tên, tên đăng nhập hoặc email không hợp lệ.');
 if(users.some(u=>u.id!==id&&(u.email.toLowerCase()===email||u.username?.toLowerCase()===username)))fail('Email hoặc tên đăng nhập đã tồn tại.',409);
 const nextRoles=b.roles??[b.roleId||old?.roleId||'MEMBER'];
 if(!Array.isArray(nextRoles)||!nextRoles.length||new Set(nextRoles).size!==nextRoles.length)fail('Vai trò không hợp lệ.');
 nextRoles.forEach(role=>need(roles,role,'id'));
 const roleId=nextRoles.includes('ADMIN')?'ADMIN':nextRoles.includes('PM')?'PM':'MEMBER',nextStatus=b.status||old?.status||'ACTIVE';
 if(!['ACTIVE','LOCKED'].includes(nextStatus))fail('Trạng thái tài khoản không hợp lệ.');
 if(old?.roles.includes('ADMIN')&&(!nextRoles.includes('ADMIN')||nextStatus!=='ACTIVE')&&!users.some(u=>u.id!==id&&u.roles.includes('ADMIN')&&u.status==='ACTIVE'))fail('Phải giữ ít nhất một Admin hoạt động.',409);
 if(b.departmentId)need(departments,b.departmentId,'id');
 data={...old,id,userId:id,username,fullName:b.fullName.trim(),email,phone:b.phone??old?.phone??'',departmentId:b.departmentId??old?.departmentId??null,department:departments.find(d=>d.id===(b.departmentId??old?.departmentId))?.name||b.department||old?.department||'',roleId,roles:nextRoles,status:nextStatus,createdAt:old?.createdAt||new Date().toISOString(),lastLogin:old?.lastLogin||null};
 if(!old&&b.password!==undefined&&handler.auth)handler.auth.register(extra,data,b.password);
 if(old&&old.username!==username){const keys=extra.authCredentials||{};const entry=Object.entries(keys).find(([,v])=>v.userId===id);if(entry){if(keys[username]&&keys[username].userId!==id)fail('Tên đăng nhập đã tồn tại.',409);delete keys[entry[0]];keys[username]=entry[1];}}
 if(old)Object.assign(old,data);else users.push(data);status=method==='POST'?201:200;
}
else if(/^\/api\/admin\/users\/[^/]+\/role$/.test(p)&&method==='PUT'){const user=need(users,p.split('/')[4],'id'),role=need(roles,b.roleId,'id');if(user.roles.includes('ADMIN')&&role.id!=='ADMIN'&&!users.some(x=>x.id!==user.id&&x.roles.includes('ADMIN')&&x.status==='ACTIVE'))fail('Phải giữ ít nhất một Admin hoạt động.',409);user.roleId=role.id;user.roles=[role.id];data=user}
else if(p==='/api/room-permissions'&&method==='PUT'){
 if(!Array.isArray(b.rooms)||b.rooms.length!==rooms.length||new Set(b.rooms.map(r=>r.id)).size!==rooms.length)fail('Cần cấu hình đủ danh sách phòng, không trùng ID.');
 for(const grant of b.rooms){need(rooms,grant.id,'roomId');if(!Array.isArray(grant.roles)||new Set(grant.roles).size!==grant.roles.length||grant.roles.some(r=>!['ADMIN','USER','GUEST','PM','MEMBER'].includes(r)))fail('Vai trò của quyền phòng không hợp lệ.');}
 const changes=b.rooms.filter(g=>JSON.stringify([...g.roles].sort())!==JSON.stringify([...(roomGrants.find(x=>x.id===g.id)?.roles||[])].sort()));
 for(const grant of changes){const old=roomGrants.find(x=>x.id===grant.id);if(old&&grant.version!==old.version+1)fail('Quyền phòng đã thay đổi. Tải lại trước khi lưu.',409);}
 roomGrants=b.rooms.map(g=>({...g,status:!g.roles.length?'blocked':g.roles.length>=3?'open':'restricted'}));
 extra.roomPermissionHistory=[...changes.map(g=>({actor:actor.fullName,room:g.name||g.id,time:new Date().toISOString(),content:'Cập nhật quyền: '+g.roles.join(', ')})),...(extra.roomPermissionHistory||[])].slice(0,1000);data={rooms:roomGrants,history:extra.roomPermissionHistory};
}
else if(/^\/api\/meetings\/[^/]+\/participants(?:\/[^/]+)?$/.test(p)&&['POST','DELETE','PATCH'].includes(method)){
 const parts=p.split('/'),m=need(meetings,parts[3],'meetingId');
 if(m.status==='CANCELLED'||m.status==='COMPLETED')fail('Cuộc họp không còn nhận thay đổi tham dự.',409);
 if(method==='POST'){
  const ids=b.userIds||b.participants||(b.userId?[b.userId]:[]);
  if(!Array.isArray(ids)||!ids.length||ids.some(id=>typeof id!=='string'))fail('Chọn danh sách tài khoản hợp lệ.');
  for(const id of ids)if(need(users,id,'id').status!=='ACTIVE')fail('Tài khoản đã ngừng hoạt động.',409);
  const next=[...new Set([m.organizerId,...m.participants,...ids])];
  const headcount=next.length+Math.max(0,(m.participantCount||m.participants.length)-m.participants.length);
  if(headcount>need(rooms,m.roomId,'roomId').capacity)fail('Số người tham dự vượt sức chứa phòng.',409);
  if(calendarBusy(ids,m.startTime,m.endTime).some(other=>other.meetingId!==m.meetingId))fail('Người được mời đã có lịch trùng.',409);
  m.participantStatuses||={};for(const id of ids)if(!m.participants.includes(id))m.participantStatuses[id]='PENDING';m.participants=next;m.participantCount=headcount;
 }else{
  const id=parts[5];if(!id||!m.participants.includes(id))fail('Người này chưa được mời.',404);
  if(method==='DELETE'){if(id===m.organizerId)fail('Không được xóa chủ trì khỏi người tham dự.',409);m.participants=m.participants.filter(x=>x!==id);m.participantCount=Math.max(m.participants.length,(m.participantCount??m.participants.length+1)-1);if(m.participantStatuses)delete m.participantStatuses[id];}
  else{if(!['ACCEPTED','DECLINED'].includes(b.status))fail('Phản hồi không hợp lệ.');m.participantStatuses||={};m.participantStatuses[id]=b.status;}
 }
 data=m.participants.map(id=>({...need(users,id,'id'),status:m.participantStatuses?.[id]||'ACCEPTED'}));
}
else if(/^\/api\/meetings\/[^/]+\/reminders$/.test(p)&&method==='POST'){need(meetings,p.split('/')[3],'meetingId');data={...b,id:randomUUID(),meetingId:p.split('/')[3],status:'SCHEDULED',demo:true};reminders.push(data);status=201}
else if(p==='/api/reminders/dispatch'&&method==='POST'){reminders.filter(r=>r.status==='SCHEDULED'&&meetings.some(m=>m.meetingId===r.meetingId&&m.status==='SCHEDULED')).forEach(r=>r.status='SENT_DEMO');data={sent:reminders.length,demo:true}}
else if((p==='/api/check-in'||p==='/api/check-out')&&method==='POST'){const id=b.meetingId,userId=b.userId||actor.id;const meeting=need(meetings,id,'meetingId');need(users,userId,'id');if(meeting.organizerId!==userId&&!meeting.participants.includes(userId))fail('Chỉ người tham dự được điểm danh.',403);if(meeting.status==='CANCELLED')fail('Cuộc họp đã hủy, không thể điểm danh.',409);const action=p.endsWith('check-in')?'in':'out',prior=attendance.filter(a=>a.meetingId===id&&a.userId===userId);if(prior.some(a=>a.action===action))fail('Đã điểm danh thao tác này.',409);if(action==='out'&&!prior.some(a=>a.action==='in'))fail('Cần check-in trước.');data={id:randomUUID(),meetingId:id,userId,action,time:new Date().toISOString(),status:'SUCCESS',demo:true};attendance.unshift(data);status=201}
else if(p.startsWith('/api/integrations/hrm/')&&method==='POST'){data={id:randomUUID(),start:new Date().toISOString(),end:new Date().toISOString(),status:'success',created:0,updated:users.length,failed:0,total:users.length,errors:[],demo:true};hrmHistory.unshift(data);status=201}
else if((p==='/api/chatbot/book-room'||p==='/api/room-bookings')&&method==='POST'){data=saveMeeting({...b,organizerId:b.organizerId||actor.id,participants:b.participants||[actor.id],title:b.title||'Đặt phòng demo'});status=201}
else if(/^\/api\/room-bookings\/[^/]+$/.test(p)&&method==='DELETE'){const m=need(meetings,p.split('/')[3],'meetingId');m.status='CANCELLED';equipmentBookings.filter(x=>x.meetingId===m.meetingId).forEach(x=>x.status='CANCELLED');reminders.filter(x=>x.meetingId===m.meetingId).forEach(x=>x.status='CANCELLED');data={ok:true,meetingId:m.meetingId}}
else if((p==='/api/availability'||p==='/api/meetings/suggestions')&&method==='POST'){const date=calendarDate(b.date||day(1)),ids=calendarPeople(b),slots=calendarSlots(date,Number(b.durationMinutes||b.duration||60),ids);send(200,p==='/api/availability'?{slots,revision}:slots);return}
else if(p==='/api/rooms/suggestions'&&method==='POST'){range(b.startTime,b.endTime);const count=Number(b.attendees||b.participantCount||1);if(!Number.isSafeInteger(count)||count<1)fail('Số người phải là số nguyên dương.');data=rooms.filter(r=>r.capacity>=count&&free(r,b.startTime,b.endTime)).sort((a,b)=>a.capacity-b.capacity).map(r=>roomDto(r,b.startTime,b.endTime))}
else if(p==='/api/chatbot/message'&&method==='POST'){data={message:'Trợ lý demo: chọn ngày, giờ, số người và phòng trước khi xác nhận. Không có kết nối AI thật.',demo:true}}
else fail('API demo chưa hỗ trợ: '+method+' '+p,404);
if(!['/api/rooms/suggestions','/api/chatbot/message'].includes(p))revision++;send(status,data)
}catch(e){if(before)handler.restore(before);if(!e.status)console.error('API operation failed:',e.name,e.message);send(e.status||500,{message:e.status?e.message:'Không thể xử lý yêu cầu. Kiểm tra dữ liệu và nhật ký máy chủ.',mode:'demo'})}}
handler.restore=function(state){rooms=copy(state.rooms);equipment=copy(state.equipment);users=copy(state.users);meetings=copy(state.meetings);equipmentBookings=copy([...new Map(state.equipmentBookings.map(b=>[b.id,b])).values()]);attendance=copy(state.attendance||[]);hrmHistory=copy(state.hrmHistory||[]);reminders=copy(state.reminders||[]);roomGrants=copy(state.roomGrants||[]);extra=copy(state.extra||{});revision=state.revision||1;};handler.setActor=value=>{actor=value};handler.exportState=()=>snapshot(true);handler.snapshot=snapshot;handler.usage=usage;module.exports=handler;
