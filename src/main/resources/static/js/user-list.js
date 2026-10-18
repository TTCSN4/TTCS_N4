"use strict";
(() => {
  const clone = (value) => structuredClone(value);
  const roles = [
    { id: "ADMIN", label: "Admin", description: "Toàn quyền quản lý hệ thống" },
    { id: "PM", label: "PM", description: "Quản lý dự án, đặt phòng thiết bị" },
    {
      id: "MEMBER",
      label: "Member",
      description: "Thành viên, tham dự cuộc họp",
    },
  ];
  const roleIds = new Set(roles.map((role) => role.id));
  let requestedRole = "ADMIN";
  try {
    requestedRole =
      new URLSearchParams(globalThis.location?.search || "").get("role") ||
      "ADMIN";
  } catch (_) {}
  const viewerRole = roleIds.has(requestedRole.toUpperCase())
    ? requestedRole.toUpperCase()
    : "MEMBER";
  const canManage = () => viewerRole === "ADMIN";
  const denial = () => ({
    ok: false,
    message: "Chỉ Admin được quản lý tài khoản và phân quyền.",
  });
  const seedRows = [
    ["nguyenva", "Nguyễn Văn A", "IT", ["ADMIN"], "ACTIVE"],
    ["trantb", "Trần Thị B", "Quản lý dự án", ["PM"], "ACTIVE"],
    ["leducd", "Lê Đức D", "Nhân sự", ["MEMBER"], "LOCKED"],
    ["phamthie", "Phạm Thị E", "Kinh doanh", ["MEMBER"], "ACTIVE"],
    ["hoangnh", "Hoàng Văn F", "Marketing", ["PM"], "ACTIVE"],
    ["ngothig", "Ngô Thị G", "IT", ["MEMBER"], "ACTIVE"],
    ["nguyenvanh", "Nguyễn Văn H", "Kinh doanh", ["MEMBER"], "ACTIVE"],
    ["buithih", "Bùi Thị H", "Marketing", ["MEMBER"], "LOCKED"],
    ["vuminhk", "Vũ Minh K", "IT", ["ADMIN", "PM"], "ACTIVE"],
    ["dangthil", "Đặng Thị L", "Nhân sự", ["MEMBER"], "ACTIVE"],
    ["tranvanm", "Trần Văn M", "Quản lý dự án", ["PM"], "ACTIVE"],
    ["phanthin", "Phan Thị N", "Kế toán", ["MEMBER"], "ACTIVE"],
    ["lequangp", "Lê Quang P", "IT", ["MEMBER"], "ACTIVE"],
    ["ngothiq", "Ngô Thị Q", "Marketing", ["PM"], "ACTIVE"],
    ["dinhvanr", "Đinh Văn R", "Hành chính", ["MEMBER"], "LOCKED"],
    ["vothis", "Võ Thị S", "Kinh doanh", ["MEMBER"], "ACTIVE"],
    ["nguyenminht", "Nguyễn Minh T", "Quản lý dự án", ["PM"], "ACTIVE"],
    ["phamthiu", "Phạm Thị U", "Nhân sự", ["MEMBER"], "ACTIVE"],
    ["tranhongv", "Trần Hồng V", "IT", ["MEMBER"], "ACTIVE"],
    ["hoangminhx", "Hoàng Minh X", "Kế toán", ["MEMBER"], "ACTIVE"],
    ["dangthiy", "Đặng Thị Y", "Marketing", ["MEMBER"], "LOCKED"],
    ["buiducz", "Bùi Đức Z", "Kinh doanh", ["PM"], "ACTIVE"],
    ["duongvana", "Dương Văn A", "Hành chính", ["MEMBER"], "ACTIVE"],
    ["truongthib", "Trương Thị B", "Nhân sự", ["MEMBER"], "ACTIVE"],
  ];
  let accounts = seedRows.map(
    ([username, fullName, department, selectedRoles, status], index) => ({
      id: "USR-" + String(index + 1).padStart(3, "0"),
      username,
      fullName,
      email: username + "@example.com",
      phone: "090" + String(index + 1).padStart(7, "0"),
      department,
      roles: selectedRoles,
      status,
      createdAt: new Date(Date.UTC(2026, 8, index + 1, 2, index)).toISOString(),
      lastLogin:
        index % 5 === 0
          ? null
          : new Date(Date.UTC(2026, 9, 18, 1, (index * 2) % 60)).toISOString(),
    }),
  );
  let sequence = accounts.length;
  const activeAdmin = (account) =>
    account.status === "ACTIVE" && account.roles.includes("ADMIN");
  const fold = (value) =>
    String(value || "")
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/đ/g, "d")
      .replace(/Đ/g, "D")
      .toLowerCase();
  const normalize = (value) => ({
    username: typeof value?.username === "string" ? value.username.trim() : "",
    fullName: typeof value?.fullName === "string" ? value.fullName.trim() : "",
    email:
      typeof value?.email === "string" ? value.email.trim().toLowerCase() : "",
    phone: typeof value?.phone === "string" ? value.phone.trim() : "",
    department:
      typeof value?.department === "string" ? value.department.trim() : "",
    roles: Array.isArray(value?.roles) ? [...new Set(value.roles)] : [],
    status: value?.status,
  });
  function validEmail(email) {
    if (email.length > 254) return false;
    const parts = email.split("@");
    if (
      parts.length !== 2 ||
      !parts[0] ||
      parts[0].length > 64 ||
      parts[0].startsWith(".") ||
      parts[0].endsWith(".") ||
      parts[0].includes("..")
    )
      return false;
    return /^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$/.test(
      email,
    );
  }
  function validate(value, id = null) {
    const errors = {};
    const entry = normalize(value);
    if (!/^[A-Za-z0-9][A-Za-z0-9._-]{2,39}$/.test(entry.username))
      errors.username =
        "Tên đăng nhập gồm 3–40 ký tự chữ, số, dấu chấm, gạch dưới hoặc gạch ngang; bắt đầu bằng chữ hoặc số.";
    else if (
      accounts.some(
        (account) =>
          account.id !== id &&
          account.username.toLowerCase() === entry.username.toLowerCase(),
      )
    )
      errors.username = "Tên đăng nhập đã được sử dụng.";
    if (entry.fullName.length < 2 || entry.fullName.length > 100)
      errors.fullName = "Họ và tên phải có từ 2 đến 100 ký tự.";
    if (!validEmail(entry.email)) errors.email = "Nhập địa chỉ email hợp lệ.";
    else if (
      accounts.some(
        (account) =>
          account.id !== id &&
          account.email.toLowerCase() === entry.email.toLowerCase(),
      )
    )
      errors.email = "Email đã được sử dụng.";
    if (
      (value?.phone != null && typeof value.phone !== "string") ||
      (entry.phone && !/^(?:0\d{9}|\+84\d{9})$/.test(entry.phone))
    )
      errors.phone =
        "Số điện thoại gồm 10 chữ số bắt đầu bằng 0 hoặc +84 và 9 chữ số.";
    if (
      (value?.department != null && typeof value.department !== "string") ||
      entry.department.length > 80
    )
      errors.department = "Phòng ban tối đa 80 ký tự.";
    if (
      !Array.isArray(value?.roles) ||
      !entry.roles.length ||
      entry.roles.some((role) => !roleIds.has(role))
    )
      errors.roles = "Chọn ít nhất một vai trò hợp lệ.";
    if (!["ACTIVE", "LOCKED"].includes(entry.status))
      errors.status = "Chọn trạng thái Hoạt động hoặc Đã khóa.";
    const password = typeof value?.password === "string" ? value.password : "";
    const confirmation =
      typeof value?.confirmPassword === "string" ? value.confirmPassword : "";
    if (!id || password !== "" || confirmation !== "") {
      if (password.length < 8 || password.length > 128)
        errors.password = "Mật khẩu phải có từ 8 đến 128 ký tự.";
      if (password !== confirmation)
        errors.confirmPassword = "Mật khẩu xác nhận không khớp.";
      else if (!confirmation && !id)
        errors.confirmPassword = "Vui lòng xác nhận mật khẩu.";
    }
    if (
      (value?.password != null && typeof value.password !== "string") ||
      (value?.confirmPassword != null &&
        typeof value.confirmPassword !== "string")
    ) {
      errors.password = "Mật khẩu phải là chuỗi từ 8 đến 128 ký tự.";
      if (typeof value?.confirmPassword !== "string")
        errors.confirmPassword = "Nhập lại mật khẩu xác nhận hợp lệ.";
    }
    const existing = id ? accounts.find((account) => account.id === id) : null;
    if (
      existing &&
      activeAdmin(existing) &&
      !activeAdmin(entry) &&
      !accounts.some((account) => account.id !== id && activeAdmin(account))
    ) {
      if (entry.status !== "ACTIVE")
        errors.status =
          "Không thể khóa Admin hoạt động cuối cùng của hệ thống.";
      if (!entry.roles.includes("ADMIN"))
        errors.roles =
          "Phải giữ ít nhất một Admin đang hoạt động trong hệ thống.";
    }
    return errors;
  }
  function all() {
    return canManage() ? clone(accounts) : [];
  }
  function search(query = "", filters = {}) {
    if (!canManage()) return [];
    const options =
      query && typeof query === "object" && !Array.isArray(query)
        ? query
        : { ...filters, query };
    const term = fold(
      options.query ?? options.search ?? options.keyword ?? "",
    ).trim();
    const role =
      typeof options.role === "string" ? options.role.toUpperCase() : "";
    const status =
      typeof options.status === "string" ? options.status.toUpperCase() : "";
    return clone(
      accounts.filter(
        (account) =>
          (!term ||
            [
              account.username,
              account.fullName,
              account.email,
              account.phone,
              account.department,
            ].some((field) => fold(field).includes(term))) &&
          (!role || role === "ALL" || account.roles.includes(role)) &&
          (!status || status === "ALL" || account.status === status),
      ),
    );
  }
  function getPage(options = {}) {
    options = options && typeof options === "object" ? options : {};
    const matched = search(options);
    const positiveInteger = (value, fallback) =>
      Number.isFinite(Number(value)) && Number(value) >= 1
        ? Math.floor(Number(value))
        : fallback;
    const pageSize = Math.min(100, positiveInteger(options.pageSize, 10));
    const total = matched.length;
    const pageCount = Math.max(1, Math.ceil(total / pageSize));
    const page = Math.min(pageCount, positiveInteger(options.page, 1));
    const offset = (page - 1) * pageSize;
    return {
      accounts: matched.slice(offset, offset + pageSize),
      total,
      page,
      pageCount,
      pageSize,
      from: total ? offset + 1 : 0,
      to: Math.min(offset + pageSize, total),
    };
  }
  function setStatuses(ids, status) {
    if (!canManage()) return denial();
    if (!["ACTIVE", "LOCKED"].includes(status))
      return {
        ok: false,
        errors: { status: "Chọn trạng thái Hoạt động hoặc Đã khóa." },
        message: "Trạng thái không hợp lệ.",
      };
    if (
      !Array.isArray(ids) ||
      !ids.length ||
      ids.some((id) => typeof id !== "string" || !id)
    )
      return { ok: false, message: "Chọn ít nhất một tài khoản hợp lệ." };
    const selectedIds = [...new Set(ids)];
    if (
      selectedIds.some((id) => !accounts.some((account) => account.id === id))
    )
      return {
        ok: false,
        message: "Có tài khoản không tồn tại; chưa thay đổi tài khoản nào.",
      };
    const next = accounts.map((account) =>
      selectedIds.includes(account.id) ? { ...account, status } : account,
    );
    if (accounts.some(activeAdmin) && !next.some(activeAdmin))
      return {
        ok: false,
        errors: {
          status: "Không thể khóa Admin hoạt động cuối cùng của hệ thống.",
        },
        message: "Phải giữ ít nhất một Admin đang hoạt động trong hệ thống.",
      };
    accounts = next;
    return {
      ok: true,
      accounts: clone(
        accounts.filter((account) => selectedIds.includes(account.id)),
      ),
    };
  }
  function importLegacy(records) {
    if (!canManage()) return denial();
    if (!Array.isArray(records))
      return {
        ok: false,
        message: "Dữ liệu tài khoản dùng chung phải là một danh sách hợp lệ.",
      };
    const roleLabels = new Map([
      ["admin", "ADMIN"],
      ["pm", "PM"],
      ["member", "MEMBER"],
      ["nguoi dat lich", "PM"],
      ["nguoi tham du", "MEMBER"],
    ]);
    const statusLabels = new Map([
      ["active", "ACTIVE"],
      ["locked", "LOCKED"],
      ["hoat dong", "ACTIVE"],
      ["tam khoa", "LOCKED"],
    ]);
    const ids = new Set();
    const emails = new Set();
    const usernames = new Set();
    const prepared = [];
    const timestamp = (value) => {
      if (value == null || value === "") return null;
      if (typeof value !== "string" || !Number.isFinite(Date.parse(value)))
        throw Error("Thời gian tài khoản dùng chung không hợp lệ.");
      return new Date(value).toISOString();
    };
    try {
      for (const record of records) {
        if (!record || typeof record !== "object" || Array.isArray(record))
          throw Error("Có tài khoản dùng chung không đúng định dạng.");
        if (
          !(
            (typeof record.id === "string" && record.id.trim()) ||
            (typeof record.id === "number" && Number.isFinite(record.id))
          )
        )
          throw Error("Mã tài khoản dùng chung không hợp lệ.");
        const id = String(record.id);
        if (ids.has(id))
          throw Error("Danh sách tài khoản dùng chung có mã trùng nhau.");
        ids.add(id);
        if (
          typeof record.name !== "string" ||
          record.name.trim().length < 2 ||
          record.name.trim().length > 100
        )
          throw Error("Họ tên tài khoản dùng chung không hợp lệ.");
        if (
          typeof record.email !== "string" ||
          !validEmail(record.email.trim().toLowerCase())
        )
          throw Error("Email tài khoản dùng chung không hợp lệ.");
        const email = record.email.trim().toLowerCase();
        if (emails.has(email))
          throw Error("Email tài khoản dùng chung bị trùng.");
        emails.add(email);
        if (
          record.dept != null &&
          (typeof record.dept !== "string" || record.dept.trim().length > 80)
        )
          throw Error("Phòng ban tài khoản dùng chung không hợp lệ.");
        const phone = record.phone == null ? "" : record.phone;
        if (
          typeof phone !== "string" ||
          (phone.trim() && !/^(?:0\d{9}|\+84\d{9})$/.test(phone.trim()))
        )
          throw Error("Số điện thoại tài khoản dùng chung không hợp lệ.");
        if (
          typeof record.role !== "string" ||
          !roleLabels.has(fold(record.role).trim())
        )
          throw Error("Vai trò tài khoản dùng chung không hợp lệ.");
        if (
          typeof record.status !== "string" ||
          !statusLabels.has(fold(record.status).trim())
        )
          throw Error("Trạng thái tài khoản dùng chung không hợp lệ.");
        let username = null;
        if (record.username != null && record.username !== "") {
          if (
            typeof record.username !== "string" ||
            !/^[A-Za-z0-9][A-Za-z0-9._-]{2,39}$/.test(record.username.trim())
          )
            throw Error("Tên đăng nhập tài khoản dùng chung không hợp lệ.");
          username = record.username.trim();
          if (usernames.has(username.toLowerCase()))
            throw Error("Tên đăng nhập tài khoản dùng chung bị trùng.");
          usernames.add(username.toLowerCase());
        }
        prepared.push({
          id,
          username,
          fullName: record.name.trim(),
          email,
          phone: phone.trim(),
          department: (record.dept || "").trim(),
          roles: [roleLabels.get(fold(record.role).trim())],
          status: statusLabels.get(fold(record.status).trim()),
          createdAt: timestamp(record.createdAt),
          lastLogin: timestamp(record.lastLogin),
        });
      }
      for (const record of prepared) {
        if (record.username) continue;
        let base = record.email
          .split("@")[0]
          .replace(/[^A-Za-z0-9._-]/g, "")
          .replace(/^[^A-Za-z0-9]+/, "")
          .toLowerCase()
          .slice(0, 40);
        if (base.length < 3) base = (base + "user").slice(0, 40);
        let username = base;
        let suffix = 1;
        while (usernames.has(username.toLowerCase())) {
          const tail = "-" + ++suffix;
          username = base.slice(0, 40 - tail.length) + tail;
        }
        usernames.add(username.toLowerCase());
        record.username = username;
      }
      accounts = prepared;
      sequence = 0;
      return {
        ok: true,
        accounts: clone(accounts),
        message: "Đã đọc tài khoản dùng chung vào dữ liệu mẫu của trang.",
      };
    } catch (error) {
      return {
        ok: false,
        message:
          error.message ||
          "Dữ liệu tài khoản dùng chung không hợp lệ; chưa thay đổi tài khoản nào.",
      };
    }
  }
  const provider = {
    viewerRole,
    allowed: canManage(),
    canManage,
    canManageAccounts: canManage,
    all,
    list: search,
    search,
    getPage,
    get(id) {
      return canManage()
        ? clone(accounts.find((account) => account.id === id) || null)
        : null;
    },
    snapshot: all,
    roleStats() {
      if (!canManage())
        return {
          total: 0,
          active: 0,
          locked: 0,
          roles: { ADMIN: 0, PM: 0, MEMBER: 0 },
          activeAdmins: 0,
        };
      return {
        total: accounts.length,
        active: accounts.filter((account) => account.status === "ACTIVE")
          .length,
        locked: accounts.filter((account) => account.status === "LOCKED")
          .length,
        roles: Object.fromEntries(
          roles.map((role) => [
            role.id,
            accounts.filter((account) => account.roles.includes(role.id))
              .length,
          ]),
        ),
        activeAdmins: accounts.filter(activeAdmin).length,
      };
    },
    validate,
    importLegacy,
    save(value, id = null) {
      if (!canManage()) return denial();
      if (!value || typeof value !== "object" || Array.isArray(value))
        return { ok: false, message: "Thông tin tài khoản không hợp lệ." };
      if (id && !accounts.some((account) => account.id === id))
        return { ok: false, message: "Tài khoản cần sửa không tồn tại." };
      const errors = validate(value, id);
      if (Object.keys(errors).length)
        return {
          ok: false,
          errors,
          message: "Vui lòng kiểm tra các trường được đánh dấu.",
        };
      const existing = id
        ? accounts.find((account) => account.id === id)
        : null;
      let nextSequence = sequence;
      let generatedId;
      do {
        generatedId = "USR-" + String(++nextSequence).padStart(3, "0");
      } while (accounts.some((entry) => entry.id === generatedId));
      const account = {
        id: existing ? existing.id : generatedId,
        ...normalize(value),
        createdAt: existing ? existing.createdAt : new Date().toISOString(),
        lastLogin: existing ? existing.lastLogin : null,
      };
      accounts = existing
        ? accounts.map((entry) => (entry.id === id ? account : entry))
        : [account, ...accounts];
      if (!existing) sequence = nextSequence;
      return { ok: true, account: clone(account) };
    },
    setStatuses,
    setStatus(id, status) {
      const result = setStatuses([id], status);
      return result.ok ? { ok: true, account: result.accounts[0] } : result;
    },
  };
  globalThis.US19Demo = { roles: clone(roles), viewerRole, provider };
})();

("use strict");
(() => {
  const { roles, viewerRole, provider } = globalThis.US19Demo;
  const byId = (id) => document.getElementById(id);
  const paths = {
    clock: '<circle cx="12" cy="12" r="9"/><path d="M12 6v6l4 2"/>',
    building:
      '<path d="M4 21h16M6 21V3h12v18M9 6h1m4 0h1M9 10h1m4 0h1M9 14h1m4 0h1M10 21v-4h4v4"/>',
    home: '<path d="m3 10 9-7 9 7M5 9v12h5v-7h4v7h5V9"/>',
    calendar:
      '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M8 3v4m8-4v4M3 10h18M7 14h3m4 0h3M7 18h3"/>',
    users:
      '<circle cx="9" cy="7" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3M16 4a3 3 0 0 1 0 6M18 14a5 5 0 0 1 3 4v3"/>',
    user: '<circle cx="12" cy="7" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2z"/>',
    rooms:
      '<rect x="3" y="4" width="18" height="17" rx="2"/><path d="M8 21V8h8v13M13 14h.01M3 9h5m8 0h5"/>',
    equipment:
      '<rect x="3" y="4" width="18" height="13" rx="2"/><path d="M8 21h8m-4-4v4M7 8h10v5H7z"/>',
    "equipment-status":
      '<rect x="3" y="3" width="18" height="18" rx="2"/><path d="m6 12 3 3 5-6m2 0h2m-2 4h2M6 18h12"/>',
    chart: '<path d="M3 3v18h18M7 17v-5m5 5V7m5 10v-8"/>',
    settings:
      '<path d="m9 3-1 3-3 1-2 3 2 2v3l3 2 1 4h6l1-4 3-2v-3l2-2-2-3-3-1-1-3z"/><circle cx="12" cy="12" r="3"/>',
    shield:
      '<path d="m12 2 8 4v6c0 5-8 10-8 10S4 17 4 12V6z"/><path d="M12 7v6m0 4h.01"/>',
    "shield-check":
      '<path d="m12 2 9 4v6c0 5-9 10-9 10S3 17 3 12V6z"/><path d="m7 12 3 3 7-7"/>',
    search: '<circle cx="10.5" cy="10.5" r="7"/><path d="m16 16 5 5"/>',
    refresh:
      '<path d="M20 9a8 8 0 0 0-14-4L3 8m0-5v5h5m-4 7a8 8 0 0 0 14 4l3-3m0 5v-5h-5"/>',
    bell: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/>',
    chevron: '<path d="m6 9 6 6 6-6"/>',
    right: '<path d="m9 5 7 7-7 7"/>',
    left: '<path d="m15 5-7 7 7 7"/>',
    plus: '<path d="M12 4v16M4 12h16"/>',
    "plus-circle": '<circle cx="12" cy="12" r="9"/><path d="M12 8v8m-4-4h8"/>',
    menu: '<path d="M3 5h18M3 12h18M3 19h18"/>',
    edit: '<path d="m15 4 5 5M4 20l5-1L21 7a2 2 0 0 0-4-4L5 15l-1 5z"/>',
    lock: '<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V6a4 4 0 0 1 8 0v4m-4 4v3"/>',
    unlock:
      '<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V6a4 4 0 0 1 7-3m-3 11v3"/>',
    more: '<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
    eye: '<path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
    "eye-off":
      '<path d="m3 3 18 18M10 5a12 12 0 0 1 11 7 14 14 0 0 1-3 4M6 6a14 14 0 0 0-3 6s3 7 9 7a12 12 0 0 0 5-1M10 10a3 3 0 0 0 4 4"/>',
    close: '<path d="m6 6 12 12M6 18 18 6"/>',
    check: '<path d="m5 12 4 4L19 6"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10h.01"/>',
  };
  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }
  function setIcon(node, name = node.dataset.icon) {
    node.innerHTML = `<svg viewBox="0 0 24 24" aria-hidden="true">${paths[name] || paths.user}</svg>`;
  }
  function icon(name) {
    const node = element("i");
    setIcon(node, name);
    return node;
  }
  document.querySelectorAll("[data-icon]").forEach((node) => setIcon(node));
  const state = { page: 1, pageSize: 10, query: "", role: "", status: "" };
  const selected = new Set();
  let visible = [];
  let toastTimer;
  let editingId = null;
  let submitted = false;
  let hasLocalChanges = false;
  let pendingConfirmation = null;
  let previousFocus = null;
  const form = byId("account-form");
  const fieldNames = [
    "username",
    "fullName",
    "email",
    "phone",
    "department",
    "password",
    "confirmPassword",
    "roles",
    "status",
  ];
  const roleInputs = [...form.querySelectorAll('[name="roles"]')];
  const rowMenu = byId("row-menu");

  function toast(message) {
    byId("toast").textContent = message;
    byId("toast").hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
      byId("toast").hidden = true;
    }, 5000);
  }
  function formatDate(value, withTime = false) {
    if (!value || !Number.isFinite(Date.parse(value))) return "—";
    const date = new Date(value);
    const options = {
      timeZone: "Asia/Ho_Chi_Minh",
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
    };
    const day = new Intl.DateTimeFormat("vi-VN", options).format(date);
    if (!withTime) return day;
    const time = new Intl.DateTimeFormat("vi-VN", {
      timeZone: "Asia/Ho_Chi_Minh",
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    }).format(date);
    return day + " " + time;
  }
  function roleBadge(id) {
    const role = roles.find((entry) => entry.id === id);
    const badge = element(
      "span",
      `u19-role-badge is-${id.toLowerCase()}`,
      role.label,
    );
    badge.prepend(
      icon(id === "ADMIN" ? "shield" : id === "PM" ? "users" : "user"),
    );
    return badge;
  }
  function statusBadge(value) {
    return element(
      "span",
      `u19-status-badge ${value === "ACTIVE" ? "is-active" : "is-locked"}`,
      value === "ACTIVE" ? "Kích hoạt" : "Tạm khóa",
    );
  }
  function rowButton(label, iconName, className, attribute, id) {
    const button = element("button", className);
    button.type = "button";
    button.dataset[attribute] = id;
    button.append(icon(iconName));
    if (label) button.append(document.createTextNode(label));
    return button;
  }
  function syncSelection() {
    const checked = visible.filter((account) =>
      selected.has(account.id),
    ).length;
    const selectPage = byId("select-page");
    selectPage.checked = visible.length > 0 && checked === visible.length;
    selectPage.indeterminate = checked > 0 && checked < visible.length;
    selectPage.disabled = !visible.length || !provider.allowed;
    byId("selection-count").textContent = selected.size
      ? `Đã chọn ${selected.size} tài khoản`
      : "Chưa chọn tài khoản";
    byId("apply-bulk").disabled =
      !provider.allowed || !selected.size || !byId("bulk-action").value;
    byId("accounts-body")
      .querySelectorAll("[data-select]")
      .forEach((input) => {
        input.checked = selected.has(input.dataset.select);
        input.closest("tr").classList.toggle("is-selected", input.checked);
      });
  }
  function closeRowMenu() {
    rowMenu.hidden = true;
    document
      .querySelectorAll("[data-more]")
      .forEach((button) => button.setAttribute("aria-expanded", "false"));
  }
  function renderPagination(data) {
    const nodes = [];
    function button(page, label, iconName, disabled = false) {
      const node = element(
        "button",
        "u19-page-button",
        iconName ? undefined : label,
      );
      node.type = "button";
      node.dataset.page = String(page);
      node.disabled = disabled;
      node.setAttribute("aria-label", label);
      if (iconName) node.append(icon(iconName));
      if (!iconName && page === data.page)
        node.setAttribute("aria-current", "page");
      return node;
    }
    nodes.push(button(data.page - 1, "Trang trước", "left", data.page === 1));
    const first = Math.max(1, Math.min(data.page - 2, data.pageCount - 4));
    const last = Math.min(data.pageCount, first + 4);
    for (let page = first; page <= last; page++)
      nodes.push(button(page, `Trang ${page}`));
    // Visible numbered controls stay short while their accessible label names each page.
    nodes
      .filter(
        (node) => node.hasAttribute("aria-current") || !node.querySelector("i"),
      )
      .forEach((node) => {
        node.textContent = node.dataset.page;
      });
    nodes.push(
      button(data.page + 1, "Trang sau", "right", data.page === data.pageCount),
    );
    byId("pagination").replaceChildren(...nodes);
  }
  function render() {
    closeRowMenu();
    const data = provider.getPage(state);
    state.page = data.page;
    visible = data.accounts;
    const currentIds = new Set(provider.all().map((account) => account.id));
    [...selected].forEach((id) => {
      if (!currentIds.has(id)) selected.delete(id);
    });
    const rows = data.accounts.map((account, index) => {
      const row = element("tr");
      row.dataset.accountId = account.id;
      const selectionCell = element("td");
      const checkbox = element("input");
      checkbox.type = "checkbox";
      checkbox.dataset.select = account.id;
      checkbox.setAttribute("aria-label", `Chọn tài khoản ${account.username}`);
      selectionCell.append(checkbox);
      const username = element("td");
      const details = rowButton(
        account.username,
        null,
        "u19-username",
        "detail",
        account.id,
      );
      details.replaceChildren(document.createTextNode(account.username));
      details.setAttribute("aria-label", `Xem chi tiết ${account.username}`);
      username.append(details);
      const roleCell = element("td");
      const badges = element("div", "u19-badge-stack");
      badges.append(...account.roles.map(roleBadge));
      roleCell.append(badges);
      const statusCell = element("td");
      statusCell.append(statusBadge(account.status));
      const actionCell = element("td");
      const actions = element("div", "u19-action-row");
      const edit = rowButton(
        "Sửa",
        "edit",
        "u19-row-button is-edit",
        "edit",
        account.id,
      );
      edit.setAttribute("aria-label", `Sửa ${account.username}`);
      const locked = account.status === "LOCKED";
      const toggle = rowButton(
        locked ? "Mở khóa" : "Khóa",
        locked ? "unlock" : "lock",
        `u19-row-button ${locked ? "is-unlock" : "is-lock"}`,
        "toggleStatus",
        account.id,
      );
      toggle.setAttribute(
        "aria-label",
        `${locked ? "Mở khóa" : "Khóa"} ${account.username}`,
      );
      const more = rowButton("", "more", "u19-more-button", "more", account.id);
      more.setAttribute("aria-label", `Thao tác khác với ${account.username}`);
      more.setAttribute("aria-haspopup", "menu");
      more.setAttribute("aria-expanded", "false");
      more.setAttribute("aria-controls", "row-menu");
      actions.append(edit, toggle, more);
      actionCell.append(actions);
      row.append(
        selectionCell,
        element("td", "", String(data.from + index)),
        username,
        element("td", "", account.fullName),
        element("td", "u19-email", account.email),
        roleCell,
        element("td", "", account.department || "—"),
        element("td", "", formatDate(account.createdAt)),
        element("td", "", formatDate(account.lastLogin, true)),
        statusCell,
        actionCell,
      );
      return row;
    });
    if (!rows.length) {
      const row = element("tr", "u19-table-empty");
      const cell = element(
        "td",
        "",
        "Không tìm thấy tài khoản phù hợp. Hãy thay đổi từ khóa hoặc bộ lọc.",
      );
      cell.colSpan = 11;
      row.append(cell);
      rows.push(row);
    }
    byId("accounts-body").replaceChildren(...rows);
    byId("pagination-summary").textContent =
      `Hiển thị ${data.from}–${data.to} / ${data.total} tài khoản`;
    renderPagination(data);
    syncSelection();
  }
  function applyFilters() {
    state.query = byId("user-search").value;
    state.role = byId("role-filter").value;
    state.status = byId("status-filter").value;
    state.page = 1;
    selected.clear();
    render();
  }
  function showFormErrors(errors = {}) {
    fieldNames.forEach((name) => {
      const message = errors[name] || "";
      const node = byId(`${name}-error`);
      node.textContent = message;
      node.hidden = !message;
      (name === "roles" ? roleInputs : [form.elements[name]]).forEach((input) =>
        input.setAttribute("aria-invalid", String(Boolean(message))),
      );
    });
  }
  function readForm() {
    return {
      ...Object.fromEntries(
        fieldNames
          .filter((name) => name !== "roles")
          .map((name) => [name, form.elements[name].value]),
      ),
      roles: roleInputs
        .filter((input) => input.checked)
        .map((input) => input.value),
    };
  }
  function clearPasswords() {
    form.querySelectorAll("[data-password]").forEach((button) => {
      const input = byId(button.dataset.password);
      input.value = "";
      input.type = "password";
      button.setAttribute("aria-pressed", "false");
      button.setAttribute(
        "aria-label",
        `Hiện mật khẩu${input.id === "confirmPassword" ? " xác nhận" : ""}`,
      );
      setIcon(button.querySelector("i"), "eye");
    });
  }
  function openAccount(id = null) {
    if (!provider.allowed) return toast("Chỉ Admin được quản lý tài khoản.");
    closeRowMenu();
    previousFocus = document.activeElement;
    const account = id ? provider.get(id) : null;
    if (id && !account) return toast("Không tìm thấy tài khoản cần sửa.");
    editingId = id;
    submitted = false;
    form.reset();
    showFormErrors();
    clearPasswords();
    byId("form-status").hidden = true;
    if (account) {
      [
        "username",
        "fullName",
        "email",
        "phone",
        "department",
        "status",
      ].forEach((name) => {
        form.elements[name].value = account[name];
      });
      roleInputs.forEach((input) => {
        input.checked = account.roles.includes(input.value);
      });
    }
    byId("account-title").textContent = id
      ? `Chỉnh sửa tài khoản ${account.username}`
      : "Thêm tài khoản";
    byId("save-account").querySelector("span").textContent = id
      ? "Lưu thay đổi"
      : "Tạo tài khoản";
    byId("password-required").hidden = Boolean(id);
    byId("confirm-required").hidden = Boolean(id);
    form.elements.password.required = !id;
    form.elements.confirmPassword.required = !id;
    byId("password-hint").textContent = id
      ? "Để trống cả hai ô mật khẩu nếu không đổi. ID và ngày tạo tài khoản được giữ nguyên."
      : "Mật khẩu từ 8 đến 128 ký tự. Tên đăng nhập và email không được trùng.";
    byId("account-dialog").showModal();
    form.elements.username.focus();
  }
  function restoreFocus() {
    (previousFocus?.isConnected ? previousFocus : byId("add-account")).focus();
  }
  function closeAccount() {
    byId("account-dialog").close();
  }
  byId("account-dialog").addEventListener("close", () => {
    clearPasswords();
    editingId = null;
    restoreFocus();
  });
  function showDetails(id) {
    const account = provider.get(id);
    if (!account) return;
    closeRowMenu();
    previousFocus = document.activeElement;
    const values = [
      ["Tên đăng nhập", account.username],
      ["Họ và tên", account.fullName],
      ["Email", account.email],
      ["Số điện thoại", account.phone || "—"],
      ["Phòng ban", account.department || "—"],
      [
        "Vai trò",
        account.roles
          .map((id) => roles.find((role) => role.id === id).label)
          .join(", "),
      ],
      ["Ngày tạo", formatDate(account.createdAt)],
      ["Đăng nhập lần cuối", formatDate(account.lastLogin, true)],
      ["Trạng thái", account.status === "ACTIVE" ? "Kích hoạt" : "Tạm khóa"],
    ];
    byId("account-details").replaceChildren(
      ...values.flatMap(([label, value]) => [
        element("dt", "", label),
        element("dd", "", value),
      ]),
    );
    byId("detail-dialog").showModal();
  }
  function confirmChange(title, message, callback) {
    previousFocus = document.activeElement;
    pendingConfirmation = callback;
    byId("confirm-title").textContent = title;
    byId("confirm-message").textContent = message;
    byId("confirm-error").hidden = true;
    byId("confirm-dialog").showModal();
  }
  function toggleAccount(id) {
    const account = provider.get(id);
    if (!account) return;
    const status = account.status === "ACTIVE" ? "LOCKED" : "ACTIVE";
    const label = status === "LOCKED" ? "Khóa" : "Mở khóa";
    confirmChange(
      `${label} tài khoản`,
      `${label} tài khoản ${account.username} trong dữ liệu mẫu?`,
      () => {
        const result = provider.setStatus(id, status);
        if (result.ok) {
          hasLocalChanges = true;
          render();
          toast(
            `Đã ${label.toLowerCase()} ${account.username} trong dữ liệu mẫu.`,
          );
        }
        return result;
      },
    );
  }
  function openRowMenu(button) {
    const id = button.dataset.more;
    const wasOpen = !rowMenu.hidden && rowMenu.dataset.accountId === id;
    closeRowMenu();
    if (wasOpen) return;
    const account = provider.get(id);
    if (!account) return;
    rowMenu.dataset.accountId = id;
    const details = rowButton("Xem chi tiết", "eye", "", "detail", id);
    const edit = rowButton("Sửa thông tin", "edit", "", "edit", id);
    [details, edit].forEach((node) => node.setAttribute("role", "menuitem"));
    rowMenu.replaceChildren(details, edit);
    rowMenu.hidden = false;
    button.setAttribute("aria-expanded", "true");
    const rect = button.getBoundingClientRect();
    rowMenu.style.left = `${Math.max(8, Math.min(innerWidth - rowMenu.offsetWidth - 8, rect.right - rowMenu.offsetWidth))}px`;
    rowMenu.style.top = `${Math.max(8, Math.min(innerHeight - rowMenu.offsetHeight - 8, rect.bottom + 5))}px`;
    details.focus();
  }
  function messageFor(result) {
    return (
      result.message ||
      Object.values(result.errors || {}).join(" ") ||
      "Không thể thực hiện thao tác. Vui lòng kiểm tra dữ liệu."
    );
  }
  function resetFilters() {
    byId("user-search").value = "";
    byId("site-search").value = "";
    byId("role-filter").value = "";
    byId("status-filter").value = "";
    applyFilters();
  }
  byId("filter-form").addEventListener("submit", (event) => {
    event.preventDefault();
    applyFilters();
  });
  byId("filter-form").addEventListener("reset", (event) => {
    event.preventDefault();
    resetFilters();
  });
  ["user-search", "role-filter", "status-filter"].forEach((id) =>
    byId(id).addEventListener(id === "user-search" ? "input" : "change", () => {
      byId("site-search").value = byId("user-search").value;
      applyFilters();
    }),
  );
  byId("site-search").addEventListener("input", (event) => {
    byId("user-search").value = event.target.value;
    applyFilters();
  });
  byId("page-size").addEventListener("change", (event) => {
    state.pageSize = Number(event.target.value);
    state.page = 1;
    render();
  });
  byId("pagination").addEventListener("click", (event) => {
    const button = event.target.closest("[data-page]");
    if (!button || button.disabled) return;
    state.page = Number(button.dataset.page);
    render();
  });
  byId("select-page").addEventListener("change", (event) => {
    visible.forEach((account) => {
      if (event.target.checked) selected.add(account.id);
      else selected.delete(account.id);
    });
    syncSelection();
  });
  byId("accounts-body").addEventListener("change", (event) => {
    const input = event.target.closest("[data-select]");
    if (!input) return;
    if (input.checked) selected.add(input.dataset.select);
    else selected.delete(input.dataset.select);
    syncSelection();
  });
  function handleRowAction(event) {
    const button = event.target.closest(
      "[data-edit], [data-detail], [data-toggle-status], [data-more]",
    );
    if (!button || !provider.allowed) return;
    if (button.dataset.edit) openAccount(button.dataset.edit);
    else if (button.dataset.detail) showDetails(button.dataset.detail);
    else if (button.dataset.toggleStatus)
      toggleAccount(button.dataset.toggleStatus);
    else if (button.dataset.more) openRowMenu(button);
  }
  byId("accounts-body").addEventListener("click", handleRowAction);
  rowMenu.addEventListener("click", handleRowAction);
  byId("bulk-action").addEventListener("change", syncSelection);
  byId("apply-bulk").addEventListener("click", () => {
    const ids = [...selected];
    const status = byId("bulk-action").value;
    if (
      !provider.allowed ||
      !ids.length ||
      !["ACTIVE", "LOCKED"].includes(status)
    )
      return;
    const label = status === "LOCKED" ? "Khóa" : "Mở khóa";
    confirmChange(
      `${label} tài khoản đã chọn`,
      `${label} ${ids.length} tài khoản đã chọn trong dữ liệu mẫu? Những tài khoản đang có trạng thái này sẽ được giữ nguyên.`,
      () => {
        const result = provider.setStatuses(ids, status);
        if (result.ok) {
          hasLocalChanges = true;
          selected.clear();
          render();
          toast(
            `Đã ${label.toLowerCase()} ${ids.length} tài khoản trong dữ liệu mẫu.`,
          );
        }
        return result;
      },
    );
  });
  byId("confirm-action").addEventListener("click", () => {
    if (!pendingConfirmation) return;
    const result = pendingConfirmation();
    if (result.ok) byId("confirm-dialog").close();
    else {
      byId("confirm-error").textContent = messageFor(result);
      byId("confirm-error").hidden = false;
    }
  });
  byId("cancel-confirm").addEventListener("click", () =>
    byId("confirm-dialog").close(),
  );
  byId("confirm-dialog").addEventListener("close", () => {
    pendingConfirmation = null;
    restoreFocus();
    flushLegacyUpdate();
  });
  byId("add-account").addEventListener("click", () => openAccount());
  byId("close-account").addEventListener("click", closeAccount);
  byId("cancel-account").addEventListener("click", closeAccount);
  byId("close-details").addEventListener("click", () =>
    byId("detail-dialog").close(),
  );
  byId("detail-dialog").addEventListener("close", () => {
    restoreFocus();
    flushLegacyUpdate();
  });
  byId("account-dialog").addEventListener("close", flushLegacyUpdate);
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    submitted = true;
    const wasEditing = Boolean(editingId);
    const result = provider.save(readForm(), editingId);
    showFormErrors(result.errors);
    if (!result.ok) {
      byId("form-status").textContent = messageFor(result);
      byId("form-status").hidden = false;
      const first = fieldNames.find((name) => result.errors?.[name]);
      if (first)
        (first === "roles" ? roleInputs[0] : form.elements[first]).focus();
      return;
    }
    hasLocalChanges = true;
    // Only account metadata enters the mock provider; credentials are immediately cleared.
    clearPasswords();
    closeAccount();
    if (wasEditing) render();
    else resetFilters();
    toast(
      `Đã ${wasEditing ? "cập nhật" : "thêm"} ${result.account.username} trong dữ liệu mẫu của trang.`,
    );
  });
  function validateField(name) {
    if (!fieldNames.includes(name)) return;
    const errors = provider.validate(readForm(), editingId);
    const error = byId(`${name}-error`);
    error.textContent = errors[name] || "";
    error.hidden = !errors[name];
    (name === "roles" ? roleInputs : [form.elements[name]]).forEach((input) =>
      input.setAttribute("aria-invalid", String(Boolean(errors[name]))),
    );
    byId("form-status").hidden = true;
  }
  form.addEventListener("input", (event) => {
    const name = event.target.name;
    if (
      fieldNames.includes(name) &&
      (submitted || !byId(`${name}-error`).hidden)
    )
      validateField(name);
    if (
      name === "password" &&
      (submitted || form.elements.confirmPassword.value)
    )
      validateField("confirmPassword");
  });
  form.addEventListener("focusout", (event) => {
    if (fieldNames.includes(event.target.name))
      validateField(event.target.name);
  });
  form.addEventListener("change", (event) => {
    if (["roles", "status"].includes(event.target.name))
      validateField(event.target.name);
  });
  form.querySelectorAll("[data-password]").forEach((button) =>
    button.addEventListener("click", () => {
      const input = byId(button.dataset.password);
      const visible = input.type === "password";
      input.type = visible ? "text" : "password";
      button.setAttribute("aria-pressed", String(visible));
      button.setAttribute(
        "aria-label",
        `${visible ? "Ẩn" : "Hiện"} mật khẩu${input.id === "confirmPassword" ? " xác nhận" : ""}`,
      );
      setIcon(button.querySelector("i"), visible ? "eye-off" : "eye");
    }),
  );

  // Read the existing US20 mock source without changing its key, shape, or participant IDs.
  // Imported data is validated as a complete batch; display always uses textContent.
  let queuedLegacyUpdate = null;
  function importLegacy(raw) {
    if (!provider.allowed || raw === null) return;
    let result;
    try {
      result = provider.importLegacy(JSON.parse(raw));
    } catch {
      result = {
        ok: false,
        message: "Dữ liệu tài khoản dùng chung không đúng định dạng.",
      };
    }
    byId("data-message").textContent = result.ok
      ? "Đã đọc danh sách mẫu dùng chung với phần phân quyền. Các thay đổi tại trang này chỉ được giữ đến khi tải lại."
      : `${messageFor(result)} Trang tiếp tục dùng danh sách hợp lệ hiện tại.`;
    byId("data-message").hidden = false;
    if (result.ok) {
      selected.clear();
      render();
    }
  }
  function showRemoteUpdateNotice() {
    byId("data-message").textContent =
      "Danh sách mẫu dùng chung đã thay đổi ở trang khác. Thay đổi bạn vừa lưu tại US19 được giữ nguyên. Tải lại trang để đọc dữ liệu dùng chung mới; khi đó các thay đổi tạm tại US19 sẽ được đặt lại.";
    byId("data-message").hidden = false;
  }
  function flushLegacyUpdate() {
    if (!queuedLegacyUpdate || document.querySelector("dialog[open]")) return;
    const update = queuedLegacyUpdate;
    queuedLegacyUpdate = null;
    if (hasLocalChanges) showRemoteUpdateNotice();
    else importLegacy(update.raw);
  }
  window.addEventListener("storage", (event) => {
    if (
      event.key !== "meeting-ui-people" ||
      event.newValue === null ||
      !provider.allowed
    )
      return;
    if (hasLocalChanges) {
      showRemoteUpdateNotice();
      return;
    }
    if (document.querySelector("dialog[open]")) {
      queuedLegacyUpdate = { raw: event.newValue };
      toast(
        "Danh sách mẫu dùng chung đã thay đổi; sẽ cập nhật sau khi đóng hộp thoại.",
      );
    } else importLegacy(event.newValue);
  });

  const viewer = roles.find((role) => role.id === viewerRole);
  byId("viewer-role").textContent = viewer.label;
  byId("accounts-panel").hidden = !provider.allowed;
  byId("add-account").hidden = !provider.allowed;
  [...form.elements].forEach((input) => {
    input.disabled = !provider.allowed;
  });
  if (!provider.allowed) {
    byId("access-message").textContent =
      `Bạn đang thử giao diện với vai trò ${viewer.label}. Chỉ Admin được xem và quản lý danh sách tài khoản.`;
    byId("access-message").hidden = false;
  } else {
    render();
    try {
      importLegacy(localStorage.getItem("meeting-ui-people"));
    } catch {
      byId("data-message").textContent =
        "Không thể đọc dữ liệu mẫu lưu trong trình duyệt; đang sử dụng danh sách mẫu của trang.";
      byId("data-message").hidden = false;
    }
  }

  function updateMenu() {
    const mobile = innerWidth <= 768;
    const expanded = mobile
      ? document.body.classList.contains("is-menu-open")
      : !document.body.classList.contains("is-menu-collapsed");
    byId("menu-toggle").setAttribute("aria-expanded", String(expanded));
    byId("sidebar").inert = !expanded;
    byId("sidebar-backdrop").hidden = !(mobile && expanded);
  }
  function closeMobileMenu() {
    document.body.classList.remove("is-menu-open");
    updateMenu();
  }
  byId("menu-toggle").addEventListener("click", () => {
    document.body.classList.toggle(
      innerWidth <= 768 ? "is-menu-open" : "is-menu-collapsed",
    );
    updateMenu();
  });
  byId("sidebar-backdrop").addEventListener("click", closeMobileMenu);
  window.addEventListener("resize", () => {
    updateMenu();
    closeRowMenu();
  });
  updateMenu();
  byId("sidebar")
    .querySelector('[aria-current="page"]')
    .addEventListener("click", closeMobileMenu);
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
    .querySelector(".u19-brand")
    .addEventListener("click", closeMobileMenu);
  document
    .querySelectorAll("[data-module]")
    .forEach((button) =>
      button.addEventListener("click", () =>
        toast(
          `${button.dataset.module} nằm ngoài bản mẫu danh sách tài khoản US19.`,
        ),
      ),
    );
  const popoverButtons = [byId("notifications"), byId("profile-button")];
  function closePopover() {
    byId("header-popover").hidden = true;
    popoverButtons.forEach((button) =>
      button.setAttribute("aria-expanded", "false"),
    );
  }
  popoverButtons.forEach((button) =>
    button.addEventListener("click", () => {
      const expanded = button.getAttribute("aria-expanded") === "true";
      closePopover();
      if (expanded) return;
      button.setAttribute("aria-expanded", "true");
      byId("header-popover").hidden = false;
      byId("header-popover").textContent =
        button.id === "notifications"
          ? "Chưa có thông báo trong bản mẫu US19."
          : `Nguyễn Văn A · ${viewer.label}. Vai trò dùng để thử giao diện, chưa có xác thực Backend.`;
    }),
  );
  document.addEventListener("click", (event) => {
    if (!event.target.closest("[data-more]") && !rowMenu.contains(event.target))
      closeRowMenu();
    if (
      !event.target.closest(".u19-header-actions") &&
      !byId("header-popover").contains(event.target)
    )
      closePopover();
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      closeRowMenu();
      closePopover();
      closeMobileMenu();
    }
  });
  rowMenu.addEventListener("keydown", (event) => {
    if (!["ArrowDown", "ArrowUp"].includes(event.key)) return;
    event.preventDefault();
    const buttons = [...rowMenu.querySelectorAll("button")];
    const index = buttons.indexOf(document.activeElement);
    buttons[
      (index + (event.key === "ArrowDown" ? 1 : -1) + buttons.length) %
        buttons.length
    ].focus();
  });
  window.addEventListener("scroll", closeRowMenu, {
    capture: true,
    passive: true,
  });
})();
