'use strict';
const icons = {
  calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 3v5m10-5v5M3 11h18M7 15h2m3 0h2m3 0h1M7 18h2m3 0h2"/>',
  home: '<path d="m3 10 9-7 9 7M5 9v12h5v-7h4v7h5V9"/>',
  rooms: '<rect x="3" y="4" width="18" height="17" rx="2"/><path d="M8 21V8h8v13M13 14h.01M3 9h5m8 0h5"/>',
  user: '<circle cx="12" cy="7" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2z"/>',
  users: '<circle cx="10" cy="7" r="4"/><path d="M3 21v-3a7 7 0 0 1 14 0v3H3m14-18a4 4 0 0 1 0 8m2 3a6 6 0 0 1 3 7"/>',
  'user-plus': '<circle cx="9" cy="7" r="4"/><path d="M2 21v-2a7 7 0 0 1 11-6M2 21h10m6-10v10m-5-5h10"/>',
  chart: '<rect x="3" y="3" width="18" height="18" rx="4"/><path d="M7 15v-4m5 4V7m5 8v-6"/>',
  'check-circle': '<circle cx="12" cy="12" r="9"/><path d="m8 12 3 3 5-6"/>',
  settings: '<path d="m9 3-1 3-3 1-2 3 2 2v3l3 2 1 4h6l1-4 3-2v-3l2-2-2-3-3-1-1-3z"/><circle cx="12" cy="12" r="3"/>',
  logout: '<path d="M10 3H4v18h6m-2-9h13m-4-4 4 4-4 4"/>',
  menu: '<path d="M3 5h18M3 12h18M3 19h18"/>',
  search: '<circle cx="10.5" cy="10.5" r="7"/><path d="m16 16 5 5"/>',
  bell: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/>',
  chevron: '<path d="m6 9 6 6 6-6"/>',
  right: '<path d="m9 5 7 7-7 7"/>',
  mail: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3 6 9 7 9-7"/>',
  shield: '<path d="m12 2 8 4v6c0 5-8 10-8 10S4 17 4 12V6zM12 6v10m0-7h4"/>',
  'shield-check': '<path d="m12 2 9 4v6c0 5-9 10-9 10S3 17 3 12V6z"/><path d="m7 12 3 3 7-7"/>',
  lock: '<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V6a4 4 0 0 1 8 0v4m-4 4v3"/>',
  'eye-off': '<path d="m3 3 18 18M10 5a12 12 0 0 1 11 7 14 14 0 0 1-3 4M6 6a14 14 0 0 0-3 6s3 7 9 7a12 12 0 0 0 5-1M10 10a3 3 0 0 0 4 4"/>',
  eye: '<path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
  info: '<circle cx="12" cy="12" r="10"/><path d="M12 11v6m0-10h.01"/>',
  refresh: '<path d="M20 9a8 8 0 0 0-14-4L3 8m0-5v5h5m-4 7a8 8 0 0 0 14 4l3-3m0 5v-5h-5"/>'
};
function renderIcon(element, name = element.dataset.icon) {
  element.innerHTML = `<svg viewBox="0 0 24 24" aria-hidden="true">${icons[name] || icons.user}</svg>`;
}
document.querySelectorAll('[data-icon]').forEach(element => renderIcon(element));
document.querySelectorAll('[data-password]').forEach(button => {
  button.addEventListener('click', () => {
    const input = document.getElementById(button.dataset.password);
    const visible = input.type === 'password';
    input.type = visible ? 'text' : 'password';
    button.setAttribute('aria-pressed', String(visible));
    button.setAttribute('aria-label', `${visible ? 'Ẩn' : 'Hiện'} mật khẩu${input.id === 'confirmPassword' ? ' xác nhận' : ''}`);
    renderIcon(button.querySelector('i'), visible ? 'eye' : 'eye-off');
  });
});

const loginForm = document.getElementById('login-form');
if (loginForm) {
  const identity = document.getElementById('identity');
  const password = document.getElementById('login-password');
  const status = document.getElementById('login-status');
  const preview = document.getElementById('preview-link');
  function validate(input) {
    const valid = input === password ? Boolean(input.value) : Boolean(input.value.trim());
    document.getElementById(`${input.id}-error`).hidden = valid;
    input.setAttribute('aria-invalid', String(!valid));
    return valid;
  }
  function showStatus(message) { status.textContent = message; status.hidden = false; }
    loginForm.addEventListener('submit', async event => {
    event.preventDefault();
    const identityValid = validate(identity);
    const passwordValid = validate(password);
    preview.hidden = true;
    if (!identityValid || !passwordValid) {
      status.hidden = true;
      (!identityValid ? identity : password).focus();
      return;
    }
    showStatus('Dang xac thuc tai khoan...');
    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          identity: identity.value.trim(),
          password: password.value
        })
      });
      if (response.ok) {
        const data = await response.json();
        const role = data.user?.roleCategory || data.user?.roleName || '';
        localStorage.setItem('auth_token', data.token);
        localStorage.setItem('auth_user', JSON.stringify(data.user));
        localStorage.setItem('auth_role', role);
        sessionStorage.setItem('auth_token', data.token);
        showStatus('Dang nhap thanh cong! Vai tro: ' + role);
        preview.hidden = false;
        preview.textContent = 'Chuyen den he thong ->';
        setTimeout(() => {
          window.location.href = '/user-permission.html';
        }, 600);
      } else {
        const err = await response.json().catch(() => ({}));
        showStatus(err.message || 'Dang nhap that bai. Vui long kiem tra lai thong tin.');
      }
    } catch (e) {
      showStatus('Loi ket noi den may chu backend.');
    }
  });
  [identity, password].forEach(input => input.addEventListener('input', () => { validate(input); status.hidden = true; preview.hidden = true; }));
  document.getElementById('forgot-password').addEventListener('click', () => { preview.hidden = true; showStatus('Chức năng khôi phục mật khẩu chưa được kết nối. Vui lòng liên hệ quản trị viên để được hỗ trợ.'); });
  document.getElementById('google-login').addEventListener('click', () => { preview.hidden = true; showStatus('Đăng nhập Google chưa được cấu hình trong hệ thống.'); });
}

const form = document.getElementById('account-form');
if (form) {
  const status = document.getElementById('form-status');
  const names = ['fullName', 'email', 'username', 'role', 'password', 'confirmPassword'];
  const initial = Object.fromEntries(names.map(name => [name, document.querySelector(`#${name}-error span`).textContent]));
  function errorFor(name) {
    const input = form.elements[name], value = input.value;
    if (name === 'fullName' && !value.trim()) return initial[name];
    if (name === 'email') {
      if (!value.trim()) return initial[name];
      if (input.validity.typeMismatch || /\s/.test(value)) return 'Email không đúng định dạng.';
    }
    if (name === 'username') {
      if (!value.trim()) return initial[name];
      if (/\s/.test(value)) return 'Tên đăng nhập không được chứa khoảng trắng.';
    }
    if (name === 'role' && !['USER', 'ADMIN'].includes(value)) return initial[name];
    if (name === 'password' && value.length < 8) return initial[name];
    if (name === 'confirmPassword' && (!value || value !== form.elements.password.value)) return initial[name];
    return '';
  }
  function validate(name) {
    const message = errorFor(name), error = document.getElementById(`${name}-error`);
    error.classList.toggle('is-clear', !message);
    error.querySelector('span').textContent = message || initial[name];
    form.elements[name].setAttribute('aria-invalid', String(Boolean(message)));
    return !message;
  }
  names.forEach(name => {
    form.elements[name].addEventListener('input', () => {
      validate(name);
      if (name === 'password' && form.elements.confirmPassword.value) validate('confirmPassword');
      status.hidden = true;
    });
    form.elements[name].addEventListener('change', () => validate(name));
  });
  form.addEventListener('submit', event => {
    event.preventDefault();
    const results = names.map(name => validate(name));
    if (results.some(valid => !valid)) { form.elements[names[results.indexOf(false)]].focus(); status.hidden = true; return; }
    status.textContent = 'Thông tin hợp lệ. Chưa thể tạo tài khoản vì hệ thống chưa có API tạo người dùng. Không có thông tin hoặc mật khẩu nào được lưu.';
    status.hidden = false;
  });
  form.addEventListener('reset', () => {
    status.hidden = true;
    names.forEach(name => {
      const error = document.getElementById(`${name}-error`);
      error.classList.remove('is-clear'); error.querySelector('span').textContent = initial[name];
      form.elements[name].removeAttribute('aria-invalid');
    });
    document.querySelectorAll('[data-password]').forEach(button => {
      document.getElementById(button.dataset.password).type = 'password';
      button.setAttribute('aria-pressed', 'false');
      button.setAttribute('aria-label', `Hiện mật khẩu${button.dataset.password === 'confirmPassword' ? ' xác nhận' : ''}`);
      renderIcon(button.querySelector('i'), 'eye-off');
    });
    form.elements.fullName.focus();
  });
  const menuToggle = document.getElementById('menu-toggle');
  function updateMenu() {
    const toggled = document.body.classList.contains('sidebar-hidden');
    const expanded = window.innerWidth <= 760 ? toggled : !toggled;
    menuToggle.setAttribute('aria-expanded', String(expanded));
    document.getElementById('sidebar').inert = !expanded;
  }
  menuToggle.addEventListener('click', () => { document.body.classList.toggle('sidebar-hidden'); updateMenu(); });
  window.addEventListener('resize', updateMenu); updateMenu();
  document.getElementById('admin-toggle').addEventListener('click', event => {
    const expanded = event.currentTarget.getAttribute('aria-expanded') === 'true';
    event.currentTarget.setAttribute('aria-expanded', String(!expanded));
    document.getElementById('admin-menu').hidden = expanded;
  });
  let toastTimer;
  function toast(message) {
    const element = document.getElementById('toast'); element.textContent = message; element.hidden = false;
    clearTimeout(toastTimer); toastTimer = setTimeout(() => { element.hidden = true; }, 4500);
  }
  document.querySelectorAll('[data-unavailable]').forEach(button => button.addEventListener('click', () => toast(`${button.dataset.unavailable} chưa được tích hợp trong trang này.`)));
  document.getElementById('site-search').addEventListener('keydown', event => { if (event.key === 'Enter' && event.currentTarget.value.trim()) toast('Tìm kiếm chưa được kết nối với máy chủ.'); });
  const popover = document.getElementById('header-popover');
  const buttons = [document.getElementById('notifications'), document.getElementById('profile-button')];
  function closePopover() { popover.hidden = true; buttons.forEach(button => button.setAttribute('aria-expanded', 'false')); }
  buttons.forEach(button => button.addEventListener('click', () => {
    const open = button.getAttribute('aria-expanded') !== 'true'; closePopover();
    popover.hidden = !open; button.setAttribute('aria-expanded', String(open));
    popover.textContent = button.id === 'notifications' ? 'Thông báo chưa được kết nối với máy chủ.' : 'Admin · Quản trị viên. Thông tin hiển thị theo giao diện mẫu.';
  }));
  document.addEventListener('click', event => { if (!event.target.closest('.header-account') && !popover.contains(event.target)) closePopover(); });
  document.addEventListener('keydown', event => { if (event.key === 'Escape') { closePopover(); if (window.innerWidth <= 760) { document.body.classList.remove('sidebar-hidden'); updateMenu(); } } });
}
