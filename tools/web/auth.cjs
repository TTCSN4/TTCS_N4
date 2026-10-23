'use strict';
const {scryptSync,randomBytes,timingSafeEqual}=require('node:crypto');
const policy=require('./access-policy.cjs');
const sessions=new Map(),attempts=new Map(),COOKIE='ttcs_session';
const roleOf=u=>u?.roles?.includes('ADMIN')?'ADMIN':u?.roles?.includes('PM')?'PM':'MEMBER';
function error(status,message){const e=Error(message);e.status=status;throw e}
function token(req){const part=(req.headers.cookie||'').split(';').map(x=>x.trim()).find(x=>x.startsWith(COOKIE+'='));return part?part.slice(COOKIE.length+1):null}
function user(req,state){const record=sessions.get(token(req));if(!record||record.expires<Date.now()){if(record)sessions.delete(token(req));return null}const u=state.users.find(u=>u.id===record.userId&&u.status==='ACTIVE');return u?{...u,userId:u.id,role:roleOf(u)}:null}
const canModule=policy.canModule;
function sameOrigin(req){if(req.headers['sec-fetch-site']==='cross-site')error(403,'Yêu cầu phải đến từ website này.');const origin=req.headers.origin;if(origin&&origin!=='http://'+req.headers.host)error(403,'Nguồn yêu cầu không hợp lệ.')}
function initialize(api){const state=api.exportState();state.extra||={};state.extra.authCredentials||={};let changed=false;for(const [username,id,role,key]of [['admin','DEMO01','ADMIN','INITIAL_ADMIN_PASSWORD'],['pm','DEMO02','PM','INITIAL_PM_PASSWORD'],['member','DEMO03','MEMBER','INITIAL_MEMBER_PASSWORD']]){if(state.extra.authCredentials[username]||Object.values(state.extra.authCredentials).some(entry=>entry.userId===id))continue;const generated=!process.env[key],password=process.env[key]||randomBytes(18).toString('base64url');if(password.length<12)error(500,'Thiếu mật khẩu khởi tạo an toàn cho '+username);const u=state.users.find(u=>u.id===id);if(!u)error(500,'Thiếu tài khoản khởi tạo.');u.username=username;u.roleId=role;u.roles=[role];u.status='ACTIVE';const salt=randomBytes(16).toString('hex');state.extra.authCredentials[username]={userId:id,salt,hash:scryptSync(password,salt,64).toString('hex')};if(generated)console.log('Tài khoản mới: '+username+' | Mật khẩu: '+password+' (hãy lưu lại; chỉ hiện khi khởi tạo)');changed=true;}if(changed){state.revision++;api.restore(state)}}
async function read(req){let text='';for await(const chunk of req){text+=chunk;if(Buffer.byteLength(text)>8192)error(413,'Yêu cầu quá lớn.')}let value;try{value=JSON.parse(text||'{}')}catch{error(400,'JSON không hợp lệ.')}if(!value||typeof value!=='object'||Array.isArray(value))error(400,'Nội dung JSON phải là object.');return value;}
async function handle(req,res,u,api){const p=u.pathname;if(!p.startsWith('/api/auth/'))return false;const send=(code,data,cookie)=>{res.writeHead(code,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store',...(cookie?{'Set-Cookie':cookie}:{})});res.end(JSON.stringify(data));};if(p==='/api/auth/login'&&req.method==='POST'){sameOrigin(req);const address=req.socket?.remoteAddress||'local',limit=attempts.get(address);if(limit&&limit.until>Date.now()&&limit.count>=10)error(429,'Đăng nhập sai nhiều lần. Thử lại sau một phút.');const input=await read(req),state=api.exportState(),username=String(input.username||'').trim().toLowerCase(),password=String(input.password||'');const credentials=state.extra.authCredentials?.[username]||state.extra.authCredentials?.[state.users.find(u=>u.email.toLowerCase()===username)?.username?.toLowerCase()],found=credentials&&state.users.find(u=>u.id===credentials.userId&&u.status==='ACTIVE');let valid=false;if(credentials&&password.length<=128){const hash=scryptSync(password,credentials.salt,64);valid=hash.length===Buffer.from(credentials.hash,'hex').length&&timingSafeEqual(hash,Buffer.from(credentials.hash,'hex'));}if(!valid||!found){const next=limit&&limit.until>Date.now()?limit:{count:0,until:Date.now()+60000};next.count++;attempts.set(address,next);error(401,'Tên đăng nhập hoặc mật khẩu không đúng.')}attempts.delete(address);sessions.delete(token(req));const sessionToken=randomBytes(32).toString('hex');sessions.set(sessionToken,{userId:found.id,expires:Date.now()+8*3600000});const identity={id:found.id,userId:found.id,username,fullName:found.fullName,role:roleOf(found)};send(200,{user:identity},COOKIE+'='+sessionToken+'; Path=/; HttpOnly; SameSite=Strict; Max-Age=28800');return true;}if(p==='/api/auth/logout'&&req.method==='POST'){sameOrigin(req);sessions.delete(token(req));send(200,{ok:true},COOKIE+'=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0');return true;}if(p==='/api/auth/me'&&req.method==='GET'){const current=user(req,api.exportState());if(!current)error(401,'Vui lòng đăng nhập.');send(200,{user:{id:current.id,userId:current.id,username:current.username,fullName:current.fullName,role:current.role}});return true;}error(404,'Không có chức năng đăng nhập này.');}
function authorize(req, u, api) {
  if (u.pathname === '/__portal/health' || u.pathname.startsWith('/api/auth/')) return;
  const state = api.exportState(), current = user(req, state);
  if (!current) error(401, 'Vui lòng đăng nhập.');
  req.actor = current; api.setActor(current);
  const p = decodeURIComponent(u.pathname), method = req.method, writing = !['GET','HEAD'].includes(method);
  if (writing) sameOrigin(req);
  if (current.role === 'ADMIN') return;
  const meetingId = /^\/api\/meetings\/([^/]+)(?:\/|$)/.exec(p)?.[1];
  const meeting = state.meetings.find(m => m.meetingId === meetingId);
  if (meeting && !policy.canReadMeeting(current, meeting)) error(403, 'Bạn không tham gia cuộc họp này.');
  if (current.role === 'MEMBER') {
    if (method === 'GET' && ['/api/demo/state','/api/demo/revision'].includes(p)) return;
    if (!policy.can(state, current.role, 0)) error(403, 'Vai trò chưa được cấp quyền xem lịch.');
    if (method === 'GET' && (['/api/demo/state','/api/demo/revision','/api/meetings','/api/mobile/calendar'].includes(p) || /^\/api\/meetings\/(?:history|[^/]+)(?:\/(?:participants|check-in-logs))?$/.test(p))) return;
    if (method === 'GET' && /^\/api\/users\/[^/]+\/meeting-history(?:\/[^/]+)?$/.test(p) && p.split('/')[3] === current.id) return;
    if (method === 'PATCH' && /^\/api\/meetings\/[^/]+\/participants\/[^/]+$/.test(p) && p.split('/')[5] === current.id && meeting?.participants.includes(current.id)) return;
    if (method === 'POST' && ['/api/check-in','/api/check-out'].includes(p)) return;
    error(403, 'Member chỉ được xem lịch, phản hồi và điểm danh của mình.');
  }
  if (p === '/api/room-permissions' && writing || /\/api\/admin\/(?:users|roles)/.test(p) || p.startsWith('/api/integrations/hrm/') && writing) error(403, 'Chỉ Admin được quản lý cấu hình hệ thống.');
  if (p.startsWith('/api/admin/reports/') && !policy.can(state, current.role, 4)) error(403, 'Chưa được cấp quyền báo cáo.');
  if (writing && p !== '/api/rooms/suggestions' && (/^\/api\/rooms(?:\/[^/]+(?:\/(?:restore|status))?)?$/.test(p) || /^\/api\/equipment(?:\/[^/]+(?:\/(?:restore|maintenance))?)?$/.test(p) && !p.endsWith('/bookings'))) error(403, 'Chỉ Admin được quản lý phòng và thiết bị.');
  if (writing && /^\/api\/users(?:\/[^/]+)?$/.test(p) && !policy.can(state, current.role, 3)) error(403, 'Chưa được cấp quyền quản lý tài khoản.');
  if (writing && /^\/api\/meetings(?:\/|$)/.test(p) && !policy.can(state, current.role, 1)) error(403, 'Chưa được cấp quyền tạo và quản lý lịch.');
  if (writing && (/^\/api\/(?:equipment\/bookings|room-bookings)(?:\/|$)/.test(p) || p.startsWith('/api/chatbot/')) && !policy.can(state, current.role, 2)) error(403, 'Chưa được cấp quyền đặt tài nguyên.');
}
function authorizeBody(req, u, b, api) {
  const current = req.actor;
  if (!current) return;
  const p = decodeURIComponent(u.pathname), state = api.exportState();
  if (p === '/api/demo/legacy') {
    const allowed = ['bookings','people','accounts','permissions','notifications','booking-draft','active-user-id','equipment','hrmConfig'];
    if (!allowed.includes(b.key)) error(403, 'Không được sửa dữ liệu hệ thống này.');
    if (['permissions','equipment','hrmConfig'].includes(b.key) && current.role !== 'ADMIN') error(403, 'Chỉ Admin được quản lý cấu hình này.');
    if (['people','accounts'].includes(b.key) && current.role !== 'ADMIN') {
      if (!policy.can(state,current.role,3)) error(403, 'Chưa được cấp quyền quản lý tài khoản.');
      if (!Array.isArray(b.value)) error(400, 'Danh sách tài khoản không hợp lệ.');
      for (const old of state.users.filter(x=>x.roles.includes('ADMIN'))) {
        const next=b.value.find(x=>String(x.id)===old.id);
        if (!next) error(403,'Không được xóa Admin.');
        const oldView=b.key==='people'?api.snapshot().legacy.people.find(x=>x.id===old.id):old;
        if (Object.keys(oldView).some(key=>JSON.stringify(next[key])!==JSON.stringify(oldView[key]))) error(403,'Không được thay đổi tài khoản Admin.');
      }
      for(const next of b.value) if((b.key==='people'?next.role==='Admin':next.roles?.includes('ADMIN'))&&!state.users.find(x=>x.id===String(next.id))?.roles.includes('ADMIN')) error(403,'Không được cấp quyền Admin.');
    }
    if (['bookings','notifications'].includes(b.key) && !policy.can(state, current.role, 1) && !(b.key==='bookings'&&policy.can(state,current.role,2))) error(403, 'Chưa được cấp quyền quản lý lịch.');
    if (b.key === 'bookings' && current.role !== 'ADMIN') {
      if (!Array.isArray(b.value)) error(400, 'Danh sách lịch không hợp lệ.');
      const previous = api.snapshot().legacy.bookings;
      for (const old of previous.filter(m => m.organizerId !== current.id)) {
        const next = b.value.find(m => String(m.id) === old.id);
        if (!next || Object.keys(old).some(k => JSON.stringify(old[k]) !== JSON.stringify(next[k]))) error(403, 'Không được thay đổi lịch của người khác.');
      }
      for (const item of b.value) {
        const old=previous.find(m=>m.id===String(item.id));
        if ((!old||old.organizerId===current.id)&&(item.organizerId||item.createdBy||old?.organizerId||current.id)!==current.id) error(403, 'Không được tạo lịch hoặc chuyển chủ trì thay người khác.');
      }
    }
  }
  if (current.role === 'ADMIN') return;
  if (['/api/check-in','/api/check-out'].includes(p) && b.userId && b.userId !== current.id) error(403, 'Chỉ được điểm danh cho chính mình.');
  if (current.role === 'MEMBER') return;
  const meetingId = /^\/api\/(?:meetings|room-bookings)\/([^/]+)/.exec(p)?.[1] || b.meetingId;
  let target = state.meetings.find(m => m.meetingId === meetingId);
  const bookingId = /^\/api\/equipment\/bookings\/(.+)$/.exec(p)?.[1];
  if (bookingId) target = state.meetings.find(m => m.meetingId === state.equipmentBookings.find(x => x.id === bookingId)?.meetingId);
  if (target && target.organizerId !== current.id && !['/api/check-in','/api/check-out'].includes(p)) error(403, 'Chỉ chủ trì được thay đổi cuộc họp này.');
  if (/^\/api\/(?:meetings|room-bookings|chatbot\/book-room)/.test(p) && b.organizerId && b.organizerId !== current.id) error(403, 'Không được giả mạo chủ trì.');
  if (/^\/api\/users(?:\/[^/]+)?$/.test(p)) {
    const old = state.users.find(x => x.id === p.split('/')[3]);
    if (old?.roles.includes('ADMIN') || b.roleId === 'ADMIN' || b.roles?.includes('ADMIN')) error(403, 'Chỉ Admin được quản lý tài khoản Admin.');
  }
}
function register(extra,user,password){if(typeof password!=='string'||password.length<8||Buffer.byteLength(password,'utf8')>72)error(400,'Mật khẩu phải từ 8 ký tự và tối đa 72 byte UTF-8.');const key=String(user.username).trim().toLowerCase();extra.authCredentials||={};if(extra.authCredentials[key])error(409,'Tên đăng nhập đã tồn tại.');const salt=randomBytes(16).toString('hex');extra.authCredentials[key]={userId:user.id,salt,hash:scryptSync(password,salt,64).toString('hex')};}
module.exports={register,initialize,handle,authorize,authorizeBody,user,canModule};
