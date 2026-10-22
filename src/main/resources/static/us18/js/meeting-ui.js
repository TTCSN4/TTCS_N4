"use strict";
const icons = {
  calendar:
    '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 3v5m10-5v5M3 11h18M7 15h2m3 0h2m3 0h1M7 18h2m3 0h2"/>',
  home: '<path d="m3 10 9-7 9 7M5 9v12h5v-7h4v7h5V9"/>',
  rooms:
    '<rect x="3" y="4" width="18" height="17" rx="2"/><path d="M8 21V8h8v13M13 14h.01M3 9h5m8 0h5"/>',
  user: '<circle cx="12" cy="7" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2z"/>',
  users:
    '<circle cx="10" cy="7" r="4"/><path d="M3 21v-3a7 7 0 0 1 14 0v3H3m14-18a4 4 0 0 1 0 8m2 3a6 6 0 0 1 3 7"/>',
  "user-plus":
    '<circle cx="9" cy="7" r="4"/><path d="M2 21v-2a7 7 0 0 1 11-6M2 21h10m6-10v10m-5-5h10"/>',
  chart:
    '<rect x="3" y="3" width="18" height="18" rx="4"/><path d="M7 15v-4m5 4V7m5 8v-6"/>',
  "check-circle": '<circle cx="12" cy="12" r="9"/><path d="m8 12 3 3 5-6"/>',
  settings:
    '<path d="m9 3-1 3-3 1-2 3 2 2v3l3 2 1 4h6l1-4 3-2v-3l2-2-2-3-3-1-1-3z"/><circle cx="12" cy="12" r="3"/>',
  logout: '<path d="M10 3H4v18h6m-2-9h13m-4-4 4 4-4 4"/>',
  menu: '<path d="M3 5h18M3 12h18M3 19h18"/>',
  search: '<circle cx="10.5" cy="10.5" r="7"/><path d="m16 16 5 5"/>',
  bell: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/>',
  chevron: '<path d="m6 9 6 6 6-6"/>',
  right: '<path d="m9 5 7 7-7 7"/>',
  mail: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3 6 9 7 9-7"/>',
  shield: '<path d="m12 2 8 4v6c0 5-8 10-8 10S4 17 4 12V6zM12 6v10m0-7h4"/>',
  "shield-check":
    '<path d="m12 2 9 4v6c0 5-9 10-9 10S3 17 3 12V6z"/><path d="m7 12 3 3 7-7"/>',
  lock: '<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V6a4 4 0 0 1 8 0v4m-4 4v3"/>',
  "eye-off":
    '<path d="m3 3 18 18M10 5a12 12 0 0 1 11 7 14 14 0 0 1-3 4M6 6a14 14 0 0 0-3 6s3 7 9 7a12 12 0 0 0 5-1M10 10a3 3 0 0 0 4 4"/>',
  eye: '<path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
  info: '<circle cx="12" cy="12" r="10"/><path d="M12 11v6m0-10h.01"/>',
  refresh:
    '<path d="M20 9a8 8 0 0 0-14-4L3 8m0-5v5h5m-4 7a8 8 0 0 0 14 4l3-3m0 5v-5h-5"/>',
};
function renderIcon(element, name = element.dataset.icon) {
  element.innerHTML = `<svg viewBox="0 0 24 24" aria-hidden="true">${icons[name] || icons.user}</svg>`;
}
Object.assign(icons, {
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 6v6l4 2"/>',
  building:
    '<path d="M4 21h16M6 21V3h12v18M9 6h1m4 0h1M9 10h1m4 0h1M9 14h1m4 0h1M10 21v-4h4v4"/>',
  equipment:
    '<rect x="3" y="4" width="18" height="13" rx="2"/><path d="M8 21h8m-4-4v4M7 8h10v5H7z"/>',
  "equipment-status":
    '<rect x="3" y="3" width="18" height="18" rx="2"/><path d="m6 12 3 3 5-6m2 0h2m-2 4h2M6 18h12"/>',
  history: '<path d="M3 11a9 9 0 1 1 2 7M3 4v7h7m2-5v6l4 2"/>',
  edit: '<path d="m15 4 5 5M4 20l5-1L21 7a2 2 0 0 0-4-4L5 15l-1 5z"/>',
  unlock:
    '<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V6a4 4 0 0 1 7-3m-3 11v3"/>',
});
document
  .querySelectorAll("[data-icon]")
  .forEach((element) => renderIcon(element));
document.querySelectorAll("[data-password]").forEach((button) => {
  button.addEventListener("click", () => {
    const input = document.getElementById(button.dataset.password);
    const visible = input.type === "password";
    input.type = visible ? "text" : "password";
    button.setAttribute("aria-pressed", String(visible));
    button.setAttribute(
      "aria-label",
      `${visible ? "Ẩn" : "Hiện"} mật khẩu${input.id === "confirmPassword" ? " xác nhận" : ""}`,
    );
    renderIcon(button.querySelector("i"), visible ? "eye" : "eye-off");
  });
});

const loginForm = document.getElementById("login-form");
if (loginForm) {
  const identity = document.getElementById("identity");
  const password = document.getElementById("login-password");
  const status = document.getElementById("login-status");
  const preview = document.getElementById("preview-link");
  function validate(input) {
    const valid =
      input === password ? Boolean(input.value) : Boolean(input.value.trim());
    document.getElementById(`${input.id}-error`).hidden = valid;
    input.setAttribute("aria-invalid", String(!valid));
    return valid;
  }
  function showStatus(message) {
    status.textContent = message;
    status.hidden = false;
  }
  loginForm.addEventListener("submit", (event) => {
    event.preventDefault();
    const identityValid = validate(identity);
    const passwordValid = validate(password);
    preview.hidden = true;
    if (!identityValid || !passwordValid) {
      status.hidden = true;
      (!identityValid ? identity : password).focus();
      return;
    }
    showStatus(
      "Giao diện đăng nhập chưa được kết nối với API xác thực. Bạn có thể xem giao diện quản trị qua liên kết bên dưới.",
    );
    preview.hidden = false;
  });
  [identity, password].forEach((input) =>
    input.addEventListener("input", () => {
      validate(input);
      status.hidden = true;
      preview.hidden = true;
    }),
  );
  document.getElementById("forgot-password").addEventListener("click", () => {
    preview.hidden = true;
    showStatus(
      "Chức năng khôi phục mật khẩu chưa được kết nối. Vui lòng liên hệ quản trị viên để được hỗ trợ.",
    );
  });
  document.getElementById("google-login").addEventListener("click", () => {
    preview.hidden = true;
    showStatus("Đăng nhập Google chưa được cấu hình trong hệ thống.");
  });
}

if (document.body.classList.contains("us18-management")) {
  (() => {
    const byId = (id) => document.getElementById(id),
      form = byId("account-form");
    const fieldNames = [
      "fullName",
      "email",
      "phone",
      "password",
      "confirmPassword",
      "roles",
      "department",
    ];
    const quickButtons = [...document.querySelectorAll("[data-role]")];
    let roles = [],
      departments = [],
      accounts = [],
      loading = false,
      ready = false,
      busy = false;
    let submitted = false,
      showAll = false,
      uncertain = false,
      toastTimer;
    function element(tag, className, text) {
      const item = document.createElement(tag);
      if (className) item.className = className;
      if (text !== undefined) item.textContent = text;
      return item;
    }
    function icon(name) {
      const item = element("i");
      renderIcon(item, name);
      return item;
    }
    const fold = (value) =>
      String(value ?? "")
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .replace(/đ/g, "d")
        .toLowerCase()
        .trim();
    function kind(role) {
      const name = fold(role.name).replace(/[ _-]/g, "");
      if (["admin", "administrator", "quantrivien"].includes(name))
        return "ADMIN";
      if (["pm", "projectmanager", "quanlyduan"].includes(name)) return "PM";
      if (["member", "user", "thanhvien"].includes(name)) return "MEMBER";
      return "OTHER";
    }
    function toast(text) {
      byId("toast").textContent = text;
      byId("toast").hidden = false;
      clearTimeout(toastTimer);
      toastTimer = setTimeout(() => {
        byId("toast").hidden = true;
      }, 5000);
    }
    function showStatus(text, error = false) {
      byId("form-status").textContent = text;
      byId("form-status").hidden = !text;
      byId("form-status").classList.toggle("is-error", error);
    }
    function csrfHeaders() {
      const token = document.querySelector('meta[name="_csrf"]')?.content;
      const header = document.querySelector(
        'meta[name="_csrf_header"]',
      )?.content;
      if (token && header) return { [header]: token };
      const cookie = document.cookie
        .split(";")
        .map((part) => part.trim())
        .find((part) => part.startsWith("XSRF-TOKEN="));
      if (!cookie) return {};
      try {
        return { "X-XSRF-TOKEN": decodeURIComponent(cookie.slice(11)) };
      } catch {
        return {};
      }
    }
    async function request(route, options = {}) {
      const controller = new AbortController(),
        timer = setTimeout(() => controller.abort(), 15000);
      const writing = options.method === "POST";
      let receivedSuccess = false;
      try {
        const adminId = window.US18Context?.adminId;
        const response = await fetch("/api" + route, {
          ...options,
          credentials: "same-origin",
          cache: "no-store",
          signal: controller.signal,
          headers: {
            Accept: "application/json",
            ...(options.body
              ? { "Content-Type": "application/json", ...csrfHeaders() }
              : {}),
            ...(typeof adminId === "string" && adminId
              ? { "X-Admin-Id": adminId }
              : {}),
          },
        });
        receivedSuccess = response.ok;
        let data = null;
        try {
          data = await response.json();
        } catch {
          /* Một số lỗi Security trả body rỗng hoặc HTML. */
        }
        if (!response.ok) {
          const error = Error(
            {
              401: "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.",
              403: writing
                ? "Backend từ chối tạo tài khoản. Kiểm tra quyền Admin và CSRF của phiên đăng nhập."
                : "Backend từ chối truy cập danh mục/tài khoản. Kiểm tra phiên Admin và cấu hình bảo mật.",
              404: "Không tìm thấy API " + route + ".",
            }[response.status] ||
              data?.message ||
              data?.detail ||
              "API trả lỗi HTTP " + response.status + ".",
          );
          error.status = response.status;
          error.fields = data?.errors || data?.fieldErrors;
          error.uncertain = writing && response.status >= 500;
          throw error;
        }
        if (data === null)
          throw Error("API trả thành công nhưng không có JSON hợp lệ.");
        return data;
      } catch (error) {
        if (error.name === "AbortError" || error instanceof TypeError) {
          const detail =
            error.name === "AbortError"
              ? "Máy chủ quá thời gian chờ."
              : "Không kết nối được API cùng máy chủ.";
          const failure = Error(
            writing
              ? detail +
                  " Chưa xác nhận tài khoản đã được tạo; kiểm tra danh sách trước khi thử lại."
              : detail,
          );
          failure.uncertain = writing;
          throw failure;
        }
        if (writing && receivedSuccess) error.uncertain = true;
        throw error;
      } finally {
        clearTimeout(timer);
      }
    }
    function mapOptions(data, type) {
      if (!Array.isArray(data))
        throw Error("API " + type + " không trả danh sách hợp lệ.");
      const seen = new Set();
      return data.map((record) => {
        const id =
          type === "vai trò" ? (record?.roleId ?? record?.id) : record?.id;
        const name =
          type === "vai trò"
            ? (record?.roleName ?? record?.name)
            : record?.name;
        if (
          typeof id !== "string" ||
          !id ||
          typeof name !== "string" ||
          !name.trim() ||
          seen.has(id)
        )
          throw Error("API " + type + " có thông tin thiếu hoặc trùng ID.");
        seen.add(id);
        return { id, name };
      });
    }
    function mapAccount(record) {
      if (
        !record ||
        typeof record.id !== "string" ||
        !record.id ||
        typeof record.fullName !== "string" ||
        typeof record.email !== "string" ||
        typeof record.roleId !== "string"
      )
        throw Error("API trả thông tin tài khoản không hợp lệ.");
      return {
        id: record.id,
        fullName: record.fullName,
        email: record.email,
        phone: record.phone || "",
        roleId: record.roleId,
        departmentId: record.departmentId || null,
        status: String(record.status || "UNKNOWN"),
        createdAt: record.createdAt || "",
      };
    }
    function mapAccounts(data) {
      if (!Array.isArray(data))
        throw Error("API tài khoản không trả danh sách hợp lệ.");
      const seen = new Set();
      return data.map((record) => {
        const item = mapAccount(record);
        if (seen.has(item.id)) throw Error("API tài khoản có ID trùng.");
        seen.add(item.id);
        return item;
      });
    }
    function roleBadge(id) {
      const role = roles.find((item) => item.id === id);
      const style = role ? kind(role).toLowerCase() : "other";
      const badge = element(
        "span",
        "u18-role-badge is-" + style,
        role?.name || id,
      );
      badge.prepend(
        icon(style === "admin" ? "shield" : style === "pm" ? "users" : "user"),
      );
      return badge;
    }
    function renderRoles() {
      byId("roles-body").replaceChildren(
        ...roles.map((role) => {
          const row = element("tr"),
            badge = element("td");
          badge.append(roleBadge(role.id));
          const descriptions = {
            ADMIN: "Quản trị hệ thống",
            PM: "Quản lý dự án",
            MEMBER: "Thành viên",
          };
          row.append(
            badge,
            element(
              "td",
              "",
              descriptions[kind(role)] || "Theo cấu hình Backend",
            ),
            element(
              "td",
              "u18-number",
              ready
                ? accounts.filter((account) => account.roleId === role.id)
                    .length
                : "—",
            ),
            element("td", "u18-api-id", role.id),
          );
          return row;
        }),
      );
    }
    function dateLabel(value) {
      const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
      return match ? match[3] + "/" + match[2] + "/" + match[1] : "Chưa có";
    }
    function renderAccounts() {
      const search = fold(byId("account-search").value);
      const filtered = accounts
        .filter((account) =>
          [account.id, account.fullName, account.email, account.phone].some(
            (value) => fold(value).includes(search),
          ),
        )
        .sort(
          (a, b) =>
            b.createdAt.localeCompare(a.createdAt) || b.id.localeCompare(a.id),
        );
      const visible = showAll ? filtered : filtered.slice(0, 3);
      byId("accounts-title").textContent = showAll
        ? "Danh sách tài khoản"
        : "Tài khoản được tạo gần đây";
      byId("show-all").replaceChildren(
        document.createTextNode(showAll ? "Thu gọn " : "Xem tất cả "),
        icon(showAll ? "chevron" : "right"),
      );
      byId("show-all").setAttribute("aria-expanded", String(showAll));
      byId("list-summary").textContent = loading
        ? "Đang tải tài khoản từ API…"
        : ready
          ? "Hiển thị " +
            visible.length +
            "/" +
            filtered.length +
            " tài khoản từ API"
          : "Chưa tải được dữ liệu tài khoản.";
      byId("empty-accounts").hidden = visible.length > 0 || loading;
      byId("empty-accounts").textContent = !ready
        ? "Chưa có dữ liệu API để hiển thị. Không dùng dữ liệu mẫu thay thế."
        : accounts.length
          ? "Không có tài khoản phù hợp bộ lọc."
          : "API chưa có tài khoản.";
      byId("accounts-body").replaceChildren(
        ...visible.map((account) => {
          const row = element("tr"),
            role = element("td"),
            status = element("td");
          role.append(roleBadge(account.roleId));
          const labels = {
            ACTIVE: "Kích hoạt",
            LOCKED: "Tạm khóa",
            INACTIVE: "Chưa kích hoạt",
          };
          status.append(
            element(
              "span",
              "u18-status-badge " +
                (account.status === "ACTIVE" ? "is-active" : "is-locked"),
              labels[account.status] || account.status,
            ),
          );
          row.append(
            element("td", "u18-api-id", account.id),
            element("td", "", account.fullName),
            element("td", "u18-email", account.email),
            role,
            element("td", "", dateLabel(account.createdAt)),
            status,
          );
          return row;
        }),
      );
    }
    function updateControls() {
      byId("account-fields").disabled = !ready || busy || loading;
      byId("save-account").disabled = !ready || busy || loading || uncertain;
      byId("save-account").querySelector("span").textContent = busy
        ? "Đang tạo…"
        : "Tạo tài khoản";
      byId("reload-data").disabled = busy || loading;
      quickButtons.forEach((button) => {
        const role = roles.find((item) => kind(item) === button.dataset.role);
        button.disabled = !ready || busy || loading || !role || window.MeetingPermissions?.role==='PM'&&button.dataset.role==='ADMIN';
        const selected = form.querySelector('[name="roles"]:checked');
        button.setAttribute(
          "aria-pressed",
          String(!!role && selected?.value === role.id),
        );
      });
      form.setAttribute("aria-busy", String(busy));
    }
    function buildOptions() {
      const oldRole = form.querySelector('[name="roles"]:checked')?.value;
      const group = byId("role-options");
      group.replaceChildren();
      for (const role of roles) {
        if(window.MeetingPermissions?.role==='PM'&&kind(role)==='ADMIN')continue;
        const label = element("label", "u18-choice"),
          input = element("input");
        input.type = "radio";
        input.name = "roles";
        input.value = role.id;
        input.required = true;
        input.checked = oldRole === role.id;
        input.setAttribute("aria-describedby", "roles-error");
        label.append(input, document.createTextNode(role.name));
        group.append(label);
      }
      const value = byId("department").value;
      byId("department").replaceChildren(
        new Option("Không chọn phòng ban", ""),
      );
      departments.forEach((item) =>
        byId("department").append(new Option(item.name, item.id)),
      );
      byId("department").value = departments.some((item) => item.id === value)
        ? value
        : "";
    }
    function resetPasswordVisibility() {
      form.querySelectorAll("[data-password]").forEach((button) => {
        byId(button.dataset.password).type = "password";
        button.setAttribute("aria-pressed", "false");
        button.setAttribute(
          "aria-label",
          button.dataset.password === "confirmPassword"
            ? "Hiện mật khẩu xác nhận"
            : "Hiện mật khẩu",
        );
        renderIcon(button.querySelector("i"), "eye-off");
      });
    }
    function showError(name, text = "") {
      const label = byId(name + "-error");
      if (!label) return;
      label.textContent = text;
      label.hidden = !text;
      const controls =
        name === "roles"
          ? form.querySelectorAll('[name="roles"]')
          : [byId(name)];
      controls.forEach((input) =>
        input?.setAttribute("aria-invalid", String(Boolean(text))),
      );
    }
    function validate() {
      const errors = {},
        password = byId("password").value,
        email = byId("email").value.trim();
      const fullName = byId("fullName").value.trim(),
        phone = byId("phone").value.trim();
      if (fullName.length < 2 || fullName.length > 100)
        errors.fullName = "Họ và tên phải có từ 2 đến 100 ký tự.";
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254)
        errors.email = "Nhập địa chỉ email hợp lệ.";
      if (phone && !/^[0-9+() .-]{7,20}$/.test(phone))
        errors.phone =
          "Số điện thoại từ 7–20 ký tự, chỉ gồm chữ số và + ( ) dấu cách, chấm, gạch ngang.";
      if (
        password.length < 8 ||
        password.length > 72 ||
        new TextEncoder().encode(password).length > 72
      )
        errors.password = "Mật khẩu từ 8 ký tự, tối đa 72 byte UTF-8 theo API.";
      if (
        !byId("confirmPassword").value ||
        byId("confirmPassword").value !== password
      )
        errors.confirmPassword = "Mật khẩu xác nhận chưa khớp.";
      const roleId = form.querySelector('[name="roles"]:checked')?.value;
      if (!roles.some((role) => role.id === roleId))
        errors.roles = "Chọn một vai trò từ danh mục Backend.";
      if (
        byId("department").value &&
        !departments.some((item) => item.id === byId("department").value)
      )
        errors.department = "Phòng ban không thuộc danh mục Backend.";
      return errors;
    }
    async function loadData() {
      if (busy || loading) return;
      if (location.protocol === "file:") {
        ready = false;
        byId("access-message").textContent =
          "Hãy mở US18 qua máy chủ của project để gọi API; không mở trực tiếp file HTML.";
        byId("access-message").hidden = false;
        renderRoles();
        renderAccounts();
        updateControls();
        return;
      }
      loading = true;
      ready = false;
      accounts = [];
      roles = [];
      departments = [];
      byId("access-message").hidden = true;
      renderAccounts();
      renderRoles();
      updateControls();
      try {
        const responses = await Promise.allSettled([
          request("/roles"),
          request("/departments"),
          request("/users"),
        ]);
        const failures = responses.filter(
          (result) => result.status === "rejected",
        );
        if (failures.length) throw failures[0].reason;
        const nextRoles = mapOptions(responses[0].value, "vai trò"),
          nextDepartments = mapOptions(responses[1].value, "phòng ban");
        const nextAccounts = mapAccounts(responses[2].value);
        if (!nextRoles.length)
          throw Error("Backend chưa có vai trò. Chưa thể tạo tài khoản.");
        roles = nextRoles;
        departments = nextDepartments;
        accounts = nextAccounts;
        ready = true;
        buildOptions();
        const adminId = window.US18Context?.adminId;
        const viewer = accounts.find(
          (item) =>
            item.id === adminId &&
            item.status === "ACTIVE" &&
            kind(
              roles.find((role) => role.id === item.roleId) || { name: "" },
            ) === "ADMIN",
        );
        byId("viewer-role").textContent = viewer
          ? "Admin (dữ liệu API)"
          : "Phiên máy chủ";
        byId("viewer-name").textContent = viewer?.fullName || "Người dùng";
      } catch (error) {
        byId("access-message").textContent =
          error.message + " Dùng nút Tải lại dữ liệu sau khi Backend sẵn sàng.";
        byId("access-message").hidden = false;
      } finally {
        loading = false;
        renderRoles();
        renderAccounts();
        updateControls();
      }
    }
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (!ready || busy || loading || uncertain) return;
      submitted = true;
      const errors = validate();
      fieldNames.forEach((name) => showError(name, errors[name]));
      if (Object.keys(errors).length) {
        showStatus("Vui lòng kiểm tra các trường được đánh dấu.", true);
        const name = fieldNames.find((name) => errors[name]);
        (name === "roles"
          ? form.querySelector('[name="roles"]')
          : byId(name)
        )?.focus();
        return;
      }
      const payload = {
        fullName: byId("fullName").value.trim(),
        email: byId("email").value.trim().toLowerCase(),
        phone: byId("phone").value.trim(),
        password: byId("password").value,
        roleId: form.querySelector('[name="roles"]:checked').value,
        departmentId: byId("department").value || null,
      };
      busy = true;
      updateControls();
      showStatus("Đang gửi yêu cầu tạo tài khoản tới Backend…");
      try {
        const response = await request("/users", {
          method: "POST",
          body: JSON.stringify(payload),
        });
        let created;
        try {
          created = mapAccount(response);
          if (
            created.email.toLowerCase() !== payload.email ||
            created.roleId !== payload.roleId ||
            created.fullName !== payload.fullName ||
            created.departmentId !== payload.departmentId
          )
            throw Error("Thông tin tài khoản trả về không khớp yêu cầu.");
        } catch (error) {
          error.uncertain = true;
          throw error;
        }
        accounts = [
          created,
          ...accounts.filter((item) => item.id !== created.id),
        ];
        form.reset();
        byId("account-search").value = "";
        byId("site-search").value = "";
        showStatus(
          "Backend đã tạo tài khoản " +
            created.fullName +
            ". Mã: " +
            created.id +
            ".",
        );
        renderAccounts();
        renderRoles();
        window.dispatchEvent(
          new CustomEvent("us18-account-created", {
            detail: { id: created.id },
          }),
        );
      } catch (error) {
        uncertain = !!error.uncertain;
        let text = error.message;
        if (error.status === 409) {
          text =
            "Email đã tồn tại hoặc dữ liệu xung đột. Kiểm tra thông tin trước khi gửi lại.";
          showError("email", text);
        }
        if (
          error.fields &&
          typeof error.fields === "object" &&
          !Array.isArray(error.fields)
        ) {
          for (const [field, value] of Object.entries(error.fields))
            showError(
              { roleId: "roles", departmentId: "department" }[field] || field,
              Array.isArray(value) ? value.join(" ") : String(value),
            );
        }
        showStatus(
          text +
            (uncertain
              ? " Chưa xác nhận kết quả lưu. Tải lại danh sách và kiểm tra email; không gửi lại ngay. Làm mới form khi đã kiểm tra."
              : ""),
          true,
        );
      } finally {
        busy = false;
        updateControls();
      }
    });
    form.addEventListener("reset", () => {
      submitted = false;
      uncertain = false;
      fieldNames.forEach((name) => showError(name));
      showStatus("");
      resetPasswordVisibility();
      queueMicrotask(() => {
        updateControls();
        byId("fullName").focus();
      });
    });
    form.addEventListener("input", (event) => {
      const name = event.target.name;
      if (submitted && fieldNames.includes(name))
        showError(name, validate()[name]);
      if (name === "password" && byId("confirmPassword").value)
        showError("confirmPassword", validate().confirmPassword);
      if (!busy && !uncertain) showStatus("");
    });
    form.addEventListener("change", () => {
      if (submitted) {
        const errors = validate();
        fieldNames.forEach((name) => showError(name, errors[name]));
      }
      updateControls();
    });
    quickButtons.forEach((button) =>
      button.addEventListener("click", () => {
        if (!ready || busy || loading) return;
        const role = roles.find((item) => kind(item) === button.dataset.role);
        if (!role) return;
        form.querySelectorAll('[name="roles"]').forEach((input) => {
          input.checked = input.value === role.id;
        });
        showError("roles");
        updateControls();
        toast(
          "Đã chọn " +
            role.name +
            " trong form. Nhấn Tạo tài khoản để gửi API.",
        );
      }),
    );
    byId("show-all").addEventListener("click", () => {
      showAll = !showAll;
      renderAccounts();
    });
    [byId("site-search"), byId("account-search")].forEach((input) =>
      input.addEventListener("input", () => {
        const other =
          input.id === "site-search"
            ? byId("account-search")
            : byId("site-search");
        other.value = input.value;
        renderAccounts();
      }),
    );
    byId("reload-data").addEventListener("click", loadData);
    const menuToggle = byId("menu-toggle");
    function updateMenu() {
      const mobile = window.innerWidth <= 768;
      const expanded = mobile
        ? document.body.classList.contains("is-menu-open")
        : !document.body.classList.contains("is-menu-collapsed");
      menuToggle.setAttribute("aria-expanded", String(expanded));
      byId("sidebar").inert = !expanded;
      byId("sidebar-backdrop").hidden = !(mobile && expanded);
    }
    function closeMobileMenu() {
      document.body.classList.remove("is-menu-open");
      updateMenu();
    }
    menuToggle.addEventListener("click", () => {
      document.body.classList.toggle(
        window.innerWidth <= 768 ? "is-menu-open" : "is-menu-collapsed",
      );
      updateMenu();
    });
    byId("sidebar-backdrop").addEventListener("click", closeMobileMenu);
    window.addEventListener("resize", updateMenu);
    updateMenu();
    byId("sidebar")
      .querySelectorAll("[data-nav-toggle]")
      .forEach((button) => {
        button.addEventListener("click", () => {
          const menu = byId(button.getAttribute("aria-controls"));
          const expanded = button.getAttribute("aria-expanded") === "true";
          button.setAttribute("aria-expanded", String(!expanded));
          menu.hidden = expanded;
        });
      });
    byId("sidebar")
      .querySelector(".u18-brand")
      .addEventListener("click", closeMobileMenu);
    document
      .querySelectorAll("[data-module]")
      .forEach((button) =>
        button.addEventListener("click", () =>
          toast(`${button.dataset.module} chưa được tích hợp trên trang US18.`),
        ),
      );
    byId("sidebar")
      .querySelector('[aria-current="page"]')
      .addEventListener("click", closeMobileMenu);
    const popover = byId("header-popover");
    const popoverButtons = [byId("notifications"), byId("profile-button")];
    function closePopover() {
      popover.hidden = true;
      popoverButtons.forEach((button) =>
        button.setAttribute("aria-expanded", "false"),
      );
    }
    popoverButtons.forEach((button) =>
      button.addEventListener("click", () => {
        const open = button.getAttribute("aria-expanded") !== "true";
        closePopover();
        if (!open) return;
        button.setAttribute("aria-expanded", "true");
        popover.hidden = false;
        popover.textContent =
          button.id === "notifications"
            ? "Trang US18 chưa kết nối API thông báo."
            : "Quyền thao tác do Backend kiểm tra. Trang không tự cấp quyền Admin.";
      }),
    );
    document.addEventListener("click", (event) => {
      if (
        !event.target.closest(".u18-header-actions") &&
        !popover.contains(event.target)
      )
        closePopover();
    });
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape") {
        closePopover();
        closeMobileMenu();
        menuToggle.focus();
      }
    });

    window.US18 = Object.freeze({ reload: loadData });
    loadData();
  })();
}
