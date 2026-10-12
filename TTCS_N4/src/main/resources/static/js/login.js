"use strict";
(() => {
  const $ = (id) => document.getElementById(id),
    KEY = "meeting.login.identifier";
  function message(text) {
    $("loginStatus").hidden = false;
    $("loginStatus").textContent = text;
  }
  try {
    const identifier = localStorage.getItem(KEY);
    if (identifier) {
      $("loginName").value = identifier;
      $("remember").checked = true;
    }
  } catch (_) {}
  $("togglePassword").addEventListener("click", () => {
    const visible = $("loginPassword").type === "password";
    $("loginPassword").type = visible ? "text" : "password";
    $("togglePassword").setAttribute("aria-label", visible ? "Ẩn mật khẩu" : "Hiện mật khẩu");
    $("togglePassword").setAttribute("aria-pressed", String(visible));
  });
  $("loginForm").addEventListener("submit", (event) => {
    event.preventDefault();
    const identifier = $("loginName").value.trim(),
      password = $("loginPassword").value;
    const errors = {
      loginName: identifier ? "" : "Vui lòng nhập tên đăng nhập hoặc email.",
      loginPassword: password ? "" : "Vui lòng nhập mật khẩu.",
    };
    for (const id of Object.keys(errors)) {
      $(id + "Error").textContent = errors[id];
      $(id).setAttribute("aria-invalid", String(!!errors[id]));
      $(id).closest(".field").classList.toggle("invalid", !!errors[id]);
    }
    const first = Object.keys(errors).find((id) => errors[id]);
    if (first) {
      $(first).focus();
      return;
    }
    try {
      $("remember").checked ? localStorage.setItem(KEY, identifier) : localStorage.removeItem(KEY);
    } catch (_) {}
    message(
      "Trang đăng nhập chưa được nối API xác thực. Tài khoản chưa được đăng nhập; vui lòng liên hệ quản trị viên.",
    );
  });
  ["loginName", "loginPassword"].forEach((id) =>
    $(id).addEventListener("input", () => {
      $("loginStatus").hidden = true;
      $(id + "Error").textContent = "";
      $(id).removeAttribute("aria-invalid");
      $(id).closest(".field").classList.remove("invalid");
    }),
  );
  $("forgot").addEventListener("click", () =>
    message(
      "Liên hệ quản trị viên để được hỗ trợ đặt lại mật khẩu. Chức năng khôi phục mật khẩu chưa được tích hợp.",
    ),
  );
  document
    .querySelectorAll("[data-provider]")
    .forEach((button) =>
      button.addEventListener("click", () =>
        message(
          "Đăng nhập bằng " + button.dataset.provider + " chưa được cấu hình trong hệ thống.",
        ),
      ),
    );
})();
