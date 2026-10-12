'use strict';

const loginForm = document.getElementById('login-form');
const identity = document.getElementById('identity');
const password = document.getElementById('login-password');
const loginStatus = document.getElementById('login-status');
const previewLink = document.getElementById('preview-link');

function validate(input, errorId, message) {
  const valid = input === password ? Boolean(input.value) : Boolean(input.value.trim());
  const error = document.getElementById(errorId);
  error.textContent = valid ? '' : message;
  error.hidden = valid;
  input.setAttribute('aria-invalid', String(!valid));
  return valid;
}

function showStatus(message) {
  loginStatus.textContent = message;
  loginStatus.hidden = false;
}

loginForm.addEventListener('submit', event => {
  event.preventDefault();
  const identityValid = validate(identity, 'identity-error', 'Vui l├▓ng nhß║¡p t├¬n ─æ─âng nhß║¡p hoß║╖c email.');
  const passwordValid = validate(password, 'password-error', 'Vui l├▓ng nhß║¡p mß║¡t khß║⌐u.');
  previewLink.hidden = true;
  if (!identityValid || !passwordValid) {
    loginStatus.hidden = true;
    (!identityValid ? identity : password).focus();
    return;
  }
  showStatus('Giao diß╗çn ─æ─âng nhß║¡p ch╞░a ─æ╞░ß╗úc kß║┐t nß╗æi vß╗¢i API x├íc thß╗▒c. Bß║ín c├│ thß╗â xem giao diß╗çn quß║ún trß╗ï qua li├¬n kß║┐t b├¬n d╞░ß╗¢i.');
  previewLink.hidden = false;
});

identity.addEventListener('input', () => {
  validate(identity, 'identity-error', 'Vui l├▓ng nhß║¡p t├¬n ─æ─âng nhß║¡p hoß║╖c email.');
  loginStatus.hidden = true;
  previewLink.hidden = true;
});
password.addEventListener('input', () => {
  validate(password, 'password-error', 'Vui l├▓ng nhß║¡p mß║¡t khß║⌐u.');
  loginStatus.hidden = true;
  previewLink.hidden = true;
});

document.getElementById('password-toggle').addEventListener('click', event => {
  const button = event.currentTarget;
  const visible = password.type === 'password';
  password.type = visible ? 'text' : 'password';
  button.setAttribute('aria-pressed', String(visible));
  button.setAttribute('aria-label', visible ? 'ß║¿n mß║¡t khß║⌐u' : 'Hiß╗çn mß║¡t khß║⌐u');
  button.querySelector('use').setAttribute('href', visible ? '#eye' : '#eye-off');
});

document.getElementById('forgot-password').addEventListener('click', () => {
  previewLink.hidden = true;
  showStatus('Chß╗⌐c n─âng kh├┤i phß╗Ñc mß║¡t khß║⌐u ch╞░a ─æ╞░ß╗úc kß║┐t nß╗æi. Vui l├▓ng li├¬n hß╗ç quß║ún trß╗ï vi├¬n ─æß╗â ─æ╞░ß╗úc hß╗ù trß╗ú.');
});
document.getElementById('google-login').addEventListener('click', () => {
  previewLink.hidden = true;
  showStatus('─É─âng nhß║¡p Google ch╞░a ─æ╞░ß╗úc cß║Ñu h├¼nh trong hß╗ç thß╗æng.');
});
