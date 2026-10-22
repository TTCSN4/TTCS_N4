(()=>{'use strict';
 const demo=window.TTCSDemo;if(!demo)return;
 let checking=false,failures=0,dirty=false,notice;
 const ignored=/search|filter|sort|page-size|^date$|^start-time$|^end-time$|^people$|^building$|^location$|^room-type$/i;
 document.addEventListener('input',event=>{const el=event.target;if(el.closest('form')&&!ignored.test(el.id||el.name||''))dirty=true;},true);
 function notify(text){if(!notice){notice=document.createElement('div');notice.id='ttcs-live-update';notice.setAttribute('role','status');notice.style.cssText='position:fixed;bottom:16px;right:16px;max-width:min(420px,calc(100vw - 32px));padding:12px 16px;border:1px solid #8fb8ed;border-radius:8px;background:#eff6ff;color:#163f70;font:14px system-ui;z-index:20000;box-shadow:0 4px 20px #163f7020';document.body.append(notice);}notice.replaceChildren(document.createTextNode(text+' '));const reload=document.createElement('button');reload.type='button';reload.textContent='Tải lại';reload.onclick=()=>location.reload();notice.append(reload);}
 async function sync(){if(checking||document.hidden)return;checking=true;try{
  const r=await fetch('/api/demo/revision',{cache:'no-store'});if(r.status===401){window.top.location.href='/login.html';return;}if(!r.ok)throw Error('revision');const version=await r.json();
  if(version.role!==demo.state.actor.role){window.top.location.reload();return;}
  if(version.revision===demo.state.revision){failures=0;return;}
  const response=await fetch('/api/demo/state',{cache:'no-store'});if(!response.ok)throw Error('state');const next=await response.json();demo.acceptState(next);failures=0;
  const event=new CustomEvent('ttcs-data-updated',{detail:{revision:next.revision,state:next},cancelable:true});if(!window.dispatchEvent(event)){notice?.remove();notice=null;return;}
  if(document.getElementById('module-frame'))return;
  const editing=dirty||document.querySelector('#layer .dialog,dialog[open] form:not([method="dialog"])');
  if(editing){notify('Có dữ liệu mới từ hệ thống. Nội dung đang nhập được giữ nguyên; hãy tải lại khi hoàn tất.');return;}
  location.reload();
 }catch{if(++failures>=3)notify('Chưa đồng bộ được dữ liệu mới. Hệ thống đang thử kết nối lại.');}finally{checking=false;}}
 window.TTCSLiveSync={sync};setInterval(sync,2000);document.addEventListener('visibilitychange',()=>{if(!document.hidden)sync();});window.addEventListener('focus',sync);
})();