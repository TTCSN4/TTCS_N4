'use strict';

const paths = {
  home: '<path d="m3 10 9-7 9 7M5 9v12h5v-7h4v7h5V9"/>',
  calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 3v5m10-5v5M3 11h18M7 15h2m3 0h2m3 0h1M7 18h2m3 0h2"/>',
  rooms: '<rect x="3" y="4" width="18" height="17" rx="2"/><path d="M8 21V8h8v13M13 14h.01M3 9h5m8 0h5"/>',
  'check-circle': '<circle cx="12" cy="12" r="9"/><path d="m8 12 3 3 5-6"/>',
  chart: '<rect x="3" y="3" width="18" height="18" rx="4"/><path d="M7 15v-4m5 4V7m5 8v-6"/>',
  settings: '<path d="m9 3-1 3-3 1-2 3 2 2v3l3 2 1 4h6l1-4 3-2v-3l2-2-2-3-3-1-1-3z"/><circle cx="12" cy="12" r="3"/>',
  users: '<circle cx="10" cy="7" r="4"/><path d="M3 21v-3a7 7 0 0 1 14 0v3H3m14-18a4 4 0 0 1 0 8m2 3a6 6 0 0 1 3 7"/>',
  user: '<circle cx="12" cy="7" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2z"/>',
  'user-plus': '<circle cx="9" cy="7" r="4"/><path d="M2 21v-2a7 7 0 0 1 11-6M2 21h10m6-10v10m-5-5h10"/>',
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
  element.innerHTML = `<svg viewBox="0 0 24 24" aria-hidden="true">${paths[name] || paths.user}</svg>`;
}
document.querySelectorAll('[data-icon]').forEach(element => renderIcon(element));

const form = document.getElementById('account-form');
const status = document.getElementById('form-status');
const fieldNames = ['fullName', 'email', 'username', 'role', 'password', 'confirmPassword'];
const initialMessages = Object.fromEntries(fieldNames.map(name => [name, document.querySelector(`#${name}-error span`).textContent]));

function errorFor(name) {
  const value = form.elements[name].value;
  if (name === 'fullName' && !value.trim()) return initialMessages[name];
  if (name === 'email') {
    if (!value.trim()) return initialMessages[name];
    if (form.elements.email.validity.typeMismatch || /\s/.test(value)) return 'Email kh├┤ng ─æ├║ng ─æß╗ïnh dß║íng.';
  }
  if (name === 'username') {
    if (!value.trim()) return initialMessages[name];
    if (/\s/.test(value)) return 'T├¬n ─æ─âng nhß║¡p kh├┤ng ─æ╞░ß╗úc chß╗⌐a khoß║úng trß║»ng.';
  }
  if (name === 'role' && !['USER', 'ADMIN'].includes(value)) return initialMessages[name];
  if (name === 'password' && value.length < 8) return initialMessages[name];
  if (name === 'confirmPassword' && (!value || value !== form.elements.password.value)) return initialMessages[name];
  return '';
}

function validateField(name) {
  const message = errorFor(name);
  const error = document.getElementById(`${name}-error`);
  error.classList.toggle('is-clear', !message);
  error.querySelector('span').textContent = message || initialMessages[name];
  form.elements[name].setAttribute('aria-invalid', String(Boolean(message)));
  return !message;
}

fieldNames.forEach(name => {
  form.elements[name].addEventListener('input', () => {
    validateField(name);
    if (name === 'password' && form.elements.confirmPassword.value) validateField('confirmPassword');
    status.hidden = true;
  });
  form.elements[name].addEventListener('change', () => validateField(name));
});

document.querySelectorAll('[data-password]').forEach(button => {
  button.addEventListener('click', () => {
    const input = document.getElementById(button.dataset.password);
    const visible = input.type === 'password';
    input.type = visible ? 'text' : 'password';
    button.setAttribute('aria-pressed', String(visible));
    button.setAttribute('aria-label', `${visible ? 'ß║¿n' : 'Hiß╗çn'} mß║¡t khß║⌐u${input.id === 'confirmPassword' ? ' x├íc nhß║¡n' : ''}`);
    renderIcon(button.querySelector('i'), visible ? 'eye' : 'eye-off');
  });
});

form.addEventListener('submit', event => {
  event.preventDefault();
  const results = fieldNames.map(name => validateField(name));
  if (results.some(valid => !valid)) {
    form.elements[fieldNames[results.indexOf(false)]].focus();
    status.hidden = true;
    return;
  }
  status.textContent = 'Th├┤ng tin hß╗úp lß╗ç. Ch╞░a thß╗â tß║ío t├ái khoß║ún v├¼ hß╗ç thß╗æng ch╞░a c├│ API tß║ío ng╞░ß╗¥i d├╣ng. Kh├┤ng c├│ th├┤ng tin hoß║╖c mß║¡t khß║⌐u n├áo ─æ╞░ß╗úc l╞░u.';
  status.hidden = false;
});

form.addEventListener('reset', () => {
  status.hidden = true;
  fieldNames.forEach(name => {
    const error = document.getElementById(`${name}-error`);
    error.classList.remove('is-clear');
    error.querySelector('span').textContent = initialMessages[name];
    form.elements[name].removeAttribute('aria-invalid');
  });
  document.querySelectorAll('[data-password]').forEach(button => {
    document.getElementById(button.dataset.password).type = 'password';
    button.setAttribute('aria-pressed', 'false');
    button.setAttribute('aria-label', `Hiß╗çn mß║¡t khß║⌐u${button.dataset.password === 'confirmPassword' ? ' x├íc nhß║¡n' : ''}`);
    renderIcon(button.querySelector('i'), 'eye-off');
  });
  form.elements.fullName.focus();
});

const menuToggle = document.getElementById('menu-toggle');
function updateMenuState() {
  const toggled = document.body.classList.contains('sidebar-hidden');
  menuToggle.setAttribute('aria-expanded', String(window.innerWidth <= 760 ? toggled : !toggled));
  const expanded = menuToggle.getAttribute('aria-expanded') === 'true';
  document.getElementById('sidebar').inert = !expanded;
}
menuToggle.addEventListener('click', () => {
  document.body.classList.toggle('sidebar-hidden');
  updateMenuState();
});
window.addEventListener('resize', updateMenuState);
updateMenuState();
document.getElementById('admin-toggle').addEventListener('click', event => {
  const button = event.currentTarget;
  const expanded = button.getAttribute('aria-expanded') === 'true';
  button.setAttribute('aria-expanded', String(!expanded));
  document.getElementById('admin-menu').hidden = expanded;
});

let toastTimer;
function showToast(message) {
  const toast = document.getElementById('toast');
  toast.textContent = message;
  toast.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { toast.hidden = true; }, 4500);
}
document.querySelectorAll('[data-unavailable]').forEach(button => {
  button.addEventListener('click', () => showToast(`${button.dataset.unavailable} ch╞░a ─æ╞░ß╗úc t├¡ch hß╗úp trong trang n├áy.`));
});
document.getElementById('site-search').addEventListener('keydown', event => {
  if (event.key === 'Enter' && event.currentTarget.value.trim()) {
    showToast('T├¼m kiß║┐m ph├▓ng hß╗ìp v├á cuß╗Öc hß╗ìp ch╞░a ─æ╞░ß╗úc kß║┐t nß╗æi vß╗¢i m├íy chß╗º.');
  }
});

const popover = document.getElementById('header-popover');
const popoverButtons = [document.getElementById('notifications'), document.getElementById('profile-button')];
popoverButtons.forEach(button => {
  button.addEventListener('click', () => {
    const shouldOpen = button.getAttribute('aria-expanded') !== 'true';
    popoverButtons.forEach(item => item.setAttribute('aria-expanded', 'false'));
    popover.hidden = !shouldOpen;
    button.setAttribute('aria-expanded', String(shouldOpen));
    popover.textContent = button.id === 'notifications'
      ? 'Th├┤ng b├ío ch╞░a ─æ╞░ß╗úc kß║┐t nß╗æi vß╗¢i m├íy chß╗º.'
      : 'Admin ┬╖ Quß║ún trß╗ï vi├¬n. Th├┤ng tin hiß╗ân thß╗ï theo giao diß╗çn mß║½u.';
  });
});
function closePopover() {
  popover.hidden = true;
  popoverButtons.forEach(button => button.setAttribute('aria-expanded', 'false'));
}
document.addEventListener('click', event => {
  if (!event.target.closest('.header-account') && !popover.contains(event.target)) closePopover();
});
document.addEventListener('keydown', event => {
  if (event.key === 'Escape') {
    closePopover();
    if (window.innerWidth <= 760) {
      document.body.classList.remove('sidebar-hidden');
      updateMenuState();
    }
  }
});
