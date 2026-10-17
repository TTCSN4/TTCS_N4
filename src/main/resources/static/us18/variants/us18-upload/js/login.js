'use strict';

const loginForm = document.getElementById('login-form');
const identity = document.getElementById('identity');
const password = document.getElementById('login-password');
const loginStatus = document.getElementById('login-status');
const previewLink = document.getElementById('preview-link');

function validate(input, errorId, message) {
  const valid = input === password ? Boolean(input.value) : Boolean(input.value.trim());
  const error = document.getElementById(errorId);
  if (error) {
    error.textContent = valid ? '' : message;
    error.hidden = valid;
  }
  input.setAttribute('aria-invalid', String(!valid));
  return valid;
}

function showStatus(message, isError = false) {
  if (loginStatus) {
    loginStatus.textContent = message;
    loginStatus.hidden = false;
    loginStatus.style.color = isError ? '#e53e3e' : '#3182ce';
  }
}

loginForm.addEventListener('submit', async event => {
  event.preventDefault();
  const identityValid = validate(identity, 'identity-error', 'Vui lòng nhập tên đăng nhập hoặc email.');
  const passwordValid = validate(password, 'password-error', 'Vui lòng nhập mật khẩu.');
  if (previewLink) previewLink.hidden = true;

  if (!identityValid || !passwordValid) {
    if (loginStatus) loginStatus.hidden = true;
    (!identityValid ? identity : password).focus();
    return;
  }

  showStatus('Đang xác thực tài khoản...');

  try {
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: identity.value.trim(),
        password: password.value
      })
    });

    if (!response.ok) {
      if (response.status === 401) {
        showStatus('Tên đăng nhập hoặc mật khẩu không chính xác.', true);
      } else if (response.status === 403) {
        showStatus('Tài khoản đã bị khóa hoặc không có quyền truy cập.', true);
      } else {
        showStatus(`Đăng nhập thất bại (Mã lỗi ${response.status}).`, true);
      }
      return;
    }

    const data = await response.json();
    localStorage.setItem('auth_token', data.token);
    sessionStorage.setItem('auth_token', data.token);
    localStorage.setItem('auth_user', JSON.stringify(data.user));

    showStatus('Đăng nhập thành công! Đang chuyển hướng...');

    setTimeout(() => {
      if (data.user && data.user.roleCategory === 'ADMIN') {
        window.location.href = '/user-permission.html';
      } else {
        window.location.href = '/us01.html';
      }
    }, 800);

  } catch (err) {
    showStatus('Không thể kết nối đến máy chủ xác thực: ' + err.message, true);
  }
});

identity.addEventListener('input', () => {
  validate(identity, 'identity-error', 'Vui lòng nhập tên đăng nhập hoặc email.');
  if (loginStatus) loginStatus.hidden = true;
});

password.addEventListener('input', () => {
  validate(password, 'password-error', 'Vui lòng nhập mật khẩu.');
  if (loginStatus) loginStatus.hidden = true;
});

const passwordToggle = document.getElementById('password-toggle');
if (passwordToggle) {
  passwordToggle.addEventListener('click', event => {
    const button = event.currentTarget;
    const visible = password.type === 'password';
    password.type = visible ? 'text' : 'password';
    button.setAttribute('aria-pressed', String(visible));
    button.setAttribute('aria-label', visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu');
    const use = button.querySelector('use');
    if (use) use.setAttribute('href', visible ? '#eye' : '#eye-off');
  });
}

const forgotBtn = document.getElementById('forgot-password');
if (forgotBtn) {
  forgotBtn.addEventListener('click', () => {
    if (previewLink) previewLink.hidden = true;
    showStatus('Vui lòng liên hệ Quản trị viên để đặt lại mật khẩu.');
  });
}
