"use strict";
(() => {
  let busy = false,
    ready = false;
  const $ = (id) => document.getElementById(id),
    form = $("createUserForm"),
    fields = ["fullName", "username", "email", "password", "confirmPassword", "phone", "position"];
  function error(id) {
    const value = $(id).value.trim();
    if (id === "fullName" && (!value || value.length < 2))
      return "Vui lòng nhập họ và tên từ 2 ký tự.";
    if (id === "username" && !/^[A-Za-z][A-Za-z0-9_.-]{2,31}$/.test(value))
      return "Tên đăng nhập từ 3 - 32 ký tự, bắt đầu bằng chữ; không có khoảng trắng.";
    if (id === "email" && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value))
      return "Email không đúng định dạng.";
    if (
      id === "password" &&
      !/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9\s])\S{8,32}$/.test($(id).value)
    )
      return "Mật khẩu phải từ 8 - 32 ký tự, bao gồm chữ hoa, chữ thường, số và ký tự đặc biệt.";
    if (id === "confirmPassword" && (!$(id).value || $(id).value !== $("password").value))
      return "Mật khẩu xác nhận chưa khớp.";
    if (id === "phone" && value && !/^\+?[0-9]{9,15}$/.test(value))
      return "Số điện thoại phải gồm 9 - 15 chữ số, có thể bắt đầu bằng +.";
    return "";
  }
  function validate(id) {
    const message = error(id),
      field = $(id).closest(".field");
    $(id + "Error").textContent = message;
    $(id).setAttribute("aria-invalid", String(!!message));
    field.classList.toggle("invalid", !!message);
    field.classList.toggle("valid", !message && !!$(id).value);
    const icon = field.querySelector(".validation-icon");
    if (icon) icon.textContent = message ? "!" : $(id).value ? "✓" : "";
    return !message;
  }
  function status() {
    const active = $("active").checked;
    $("activeLabel").textContent = active ? "Kích hoạt" : "Chưa kích hoạt";
    $("activeHelp").textContent = active
      ? "Tài khoản sẽ được kích hoạt và có thể đăng nhập."
      : "Tài khoản chưa được phép đăng nhập cho đến khi kích hoạt.";
  }
  fields.forEach((id) => {
    $(id).addEventListener("blur", () => validate(id));
    $(id).addEventListener("input", () => {
      if ($(id).getAttribute("aria-invalid") === "true") validate(id);
      if (id === "password" && $("confirmPassword").value) validate("confirmPassword");
      $("formStatus").hidden = true;
    });
  });
  document.querySelectorAll("[data-password]").forEach((button) =>
    button.addEventListener("click", () => {
      const input = $(button.dataset.password),
        show = input.type === "password";
      input.type = show ? "text" : "password";
      button.setAttribute("aria-label", show ? "Ẩn mật khẩu" : "Hiện mật khẩu");
      button.setAttribute("aria-pressed", String(show));
    }),
  );
  $("active").addEventListener("change", status);
  $("description").addEventListener(
    "input",
    () => ($("descriptionCount").textContent = $("description").value.length + "/255"),
  );
  $("menu").addEventListener("click", () => document.body.classList.toggle("sidebar-collapsed"));
  $("reset").addEventListener("click", () => {
    if (!confirm("Đặt lại các thông tin đang nhập?")) return;
    form.reset();
    $("roleError").textContent = "";
    fields.forEach((id) => {
      $(id + "Error").textContent = "";
      $(id).removeAttribute("aria-invalid");
      $(id).closest(".field").classList.remove("valid", "invalid");
      const icon = $(id).closest(".field").querySelector(".validation-icon");
      if (icon) icon.textContent = "";
    });
    document.querySelectorAll("[data-password]").forEach((button) => {
      $(button.dataset.password).type = "password";
      button.setAttribute("aria-label", "Hiện mật khẩu");
      button.setAttribute("aria-pressed", "false");
    });
    $("descriptionCount").textContent = "0/255";
    $("formStatus").hidden = true;
    status();
    $("fullName").focus();
  });
  async function request(path, options = {}) {
    const controller = new AbortController(),
      timer = setTimeout(() => controller.abort(), 15000);
    try {
      const response = await fetch("/api" + path, {
        ...options,
        headers: {
          Accept: "application/json",
          ...(options.body
            ? { "Content-Type": "application/json" }
            : {}),
        },
        credentials: "same-origin",
        signal: controller.signal,
      });
      let data;
      try {
        data = await response.json();
      } catch (_) {
        throw Error("API không trả JSON hợp lệ.");
      }
      if (!response.ok) {
        const error = new Error(
          data.message ||
            data.detail ||
            {
              401: "Vui lòng đăng nhập.",
              403: "Bạn không có quyền quản trị để tạo tài khoản.",
              404: "Không tìm thấy API.",
            }[response.status] ||
            "API trả lỗi HTTP " + response.status,
        );
        error.fields = data.errors;
        error.status = response.status;
        throw error;
      }
      return data;
    } catch (e) {
      if (e.name === "AbortError")
        throw Error("Máy chủ chưa phản hồi. Kiểm tra danh sách tài khoản trước khi thử lại.");
      if (e instanceof TypeError) throw Error("Không kết nối được backend cổng 8080.");
      throw e;
    } finally {
      clearTimeout(timer);
    }
  }
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (busy || !ready) return;
    const invalid = fields.filter((id) => !validate(id));
    $("roleError").textContent = $("role").value ? "" : "Vui lòng chọn vai trò.";
    if (!$("role").value) invalid.push("role");
    $("formStatus").hidden = false;
    if (invalid.length) {
      $("formStatus").textContent = "Vui lòng kiểm tra thông tin được đánh dấu.";
      const details = $(invalid[0]).closest("details");
      if (details) details.open = true;
      $(invalid[0]).focus();
      return;
    }
    const payload = {
      fullName: $("fullName").value.trim(),
      username: $("username").value.trim(),
      email: $("email").value.trim(),
      password: $("password").value,
      phone: $("phone").value.trim(),
      roleId: $("role").value,
      departmentId: $("department").value || null,
      status: $("active").checked ? "ACTIVE" : "INACTIVE",
      position: $("position").value.trim(),
      description: $("description").value.trim(),
    };
    busy = true;
    const controls = [...form.querySelectorAll("input,select,textarea,button")];
    controls.forEach((el) => (el.disabled = true));
    $("formStatus").textContent = "Đang tạo tài khoản…";
    try {
      const created = await request("/users", { method: "POST", body: JSON.stringify(payload) });
      if (!created || !created.id) throw Error("API không trả mã tài khoản.");
      $("formStatus").textContent =
        "Đã tạo tài khoản " + created.fullName + " thành công. Mã: " + created.id + ".";
      $("password").value = "";
      $("confirmPassword").value = "";
    } catch (e) {
      $("formStatus").textContent = e.message;
      if (e.fields && typeof e.fields === "object") {
        for (const [field, text] of Object.entries(e.fields)) {
          const id = { roleId: "role", departmentId: "department" }[field] || field;
          const input = $(id),
            label = $(id + "Error");
          if (input && label) {
            label.textContent = text;
            input.setAttribute("aria-invalid", "true");
            input.closest(".field")?.classList.add("invalid");
          }
        }
      }
      if (e.message.includes("đăng nhập")) {
        $("usernameError").textContent = e.message;
        $("username").setAttribute("aria-invalid", "true");
        $("username").closest(".field").classList.add("invalid");
      }
      if (e.status === 409 && e.message.toLowerCase().includes("email")) {
        $("emailError").textContent = e.message;
        $("email").setAttribute("aria-invalid", "true");
        $("email").closest(".field").classList.add("invalid");
      }
    } finally {
      busy = false;
      controls.forEach((el) => (el.disabled = false));
    }
  });
  async function loadOptions() {
    const submit = form.querySelector("[type=submit]");
    submit.disabled = true;
    try {

      const [roles, departments] = await Promise.all([request("/roles"), request("/departments")]);
      if (!Array.isArray(roles) || !Array.isArray(departments))
        throw Error("Danh sách vai trò hoặc phòng ban không hợp lệ.");
      $("role").replaceChildren();
      const emptyRole = document.createElement("option");
      emptyRole.value = "";
      emptyRole.textContent = "Chọn vai trò tài khoản";
      $("role").append(emptyRole);
      $("roleGuide").replaceChildren();
      for (const role of roles) {
        const option = document.createElement("option");
        option.value = role.id;
        option.textContent =
          { ADMIN: "Quản trị viên", BOOKER: "Người đặt lịch", ATTENDEE: "Người tham dự" }[
            role.id
          ] || role.name;
        const card = document.createElement("article");
        card.className = "role-guide";
        const title = document.createElement("strong");
        title.textContent =
          { ADMIN: "Quản trị viên", BOOKER: "Người đặt lịch", ATTENDEE: "Người tham dự" }[
            role.id
          ] || role.name;
        const description = document.createElement("p");
        description.textContent =
          {
            ADMIN: "Quản lý hệ thống, người dùng và các tài nguyên.",
            BOOKER: "Tạo và quản lý lịch họp theo quyền được cấp.",
            ATTENDEE: "Tham gia các cuộc họp được mời.",
          }[role.id] || "Quyền của vai trò được cấu hình trong hệ thống.";
        card.append(title, description);
        $("roleGuide").append(card);
        $("role").append(option);
      }
      $("department").replaceChildren();
      const empty = document.createElement("option");
      empty.value = "";
      empty.textContent = "Không chọn phòng ban";
      $("department").append(empty);
      for (const department of departments) {
        const option = document.createElement("option");
        option.value = department.id;
        option.textContent = department.name;
        $("department").append(option);
      }
      if (!roles.length) throw Error("Backend chưa có vai trò.");
      ready = true;
      submit.disabled = false;
    } catch (e) {
      $("formStatus").hidden = false;
      $("formStatus").textContent =
        e.message + " Tải lại trang để thử lại.";
    }
  }
  loadOptions();
  status();
})();
