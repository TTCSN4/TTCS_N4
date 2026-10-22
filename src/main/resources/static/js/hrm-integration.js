(() => {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const form = $("configuration");
  let saved = null;
  let connected = false;
  let busy = false;
  let sequence = 0;
  let history = [];
  const labels = { success: "Thành công", failed: "Thất bại", running: "Đang đồng bộ" };
  const selected = (name) =>
    [...document.querySelectorAll(`input[name="${name}"]:checked`)].map((el) => el.value);
  const wait = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));
  const dateText = (value) =>
    value
      ? new Intl.DateTimeFormat("vi-VN", {
          timeZone: "Asia/Ho_Chi_Minh",
          day: "2-digit",
          month: "2-digit",
          year: "numeric",
          hour: "2-digit",
          minute: "2-digit",
          hour12: false,
        }).format(new Date(value))
      : "—";
  const dayKey = (value) =>
    new Intl.DateTimeFormat("en-CA", {
      timeZone: "Asia/Ho_Chi_Minh",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).format(new Date(value));
  function element(tag, text, className) {
    const node = document.createElement(tag);
    if (text != null) node.textContent = text;
    if (className) node.className = className;
    return node;
  }
  function notify(text, error = false) {
    $("notice").textContent = text;
    $("notice").className = "notice" + (error ? " error-notice" : "");
    $("notice").hidden = false;
  }
  function config() {
    return {
      type: $("systemType").value,
      name: $("systemName").value.trim(),
      url: $("baseUrl").value.trim(),
      auth: $("authMethod").value,
      token: $("token").value.trim(),
      schedule: $("schedule").value,
      time: $("syncTime").value,
      data: selected("configData"),
    };
  }
  function validate() {
    const value = config();
    const errors = {};
    if (!value.name) errors.systemName = "Vui lòng nhập tên hệ thống.";
    try {
      const url = new URL(value.url);
      if (!["https:", "http:"].includes(url.protocol) || url.username || url.password)
        throw Error();
    } catch (_) {
      errors.baseUrl = "Nhập URL HTTP/HTTPS hợp lệ, không chứa thông tin đăng nhập.";
    }
    if (!value.token) errors.token = "Vui lòng nhập token hoặc API key.";
    if (value.schedule !== "off" && !value.time) errors.syncTime = "Chọn giờ đồng bộ tự động.";
    if (!value.data.length) errors.data = "Chọn ít nhất một loại dữ liệu đồng bộ.";
    for (const id of ["systemName", "baseUrl", "token", "syncTime", "data"]) {
      $(id + "Error").textContent = errors[id] || "";
      if ($(id)) $(id).setAttribute("aria-invalid", String(Boolean(errors[id])));
    }
    if (Object.keys(errors).length) {
      const first = Object.keys(errors).find((id) => $(id));
      if (first) $(first).focus();
      return null;
    }
    return value;
  }
  function status(state, title, description) {
    const classes = { connected: "success", disconnected: "neutral", checking: "running" };
    const className = classes[state];
    $("connectionBadge").className = "badge " + className;
    $("connectionBadge").textContent =
      state === "connected"
        ? "✓ Đã kết nối (mô phỏng)"
        : state === "checking"
          ? "Đang kiểm tra"
          : "Chưa kết nối";
    $("connectionBanner").className = "connection-banner " + className;
    $("connectionIcon").textContent =
      state === "connected" ? "✓" : state === "checking" ? "⟳" : "○";
    $("connectionTitle").textContent = title;
    $("connectionDescription").textContent = description;
    $("statusText").textContent =
      state === "connected"
        ? "Hoạt động (mô phỏng)"
        : state === "checking"
          ? "Đang kiểm tra"
          : "Chưa kiểm tra";
    $("syncNow").disabled = !connected || busy;
  }
  function lock(locked) {
    busy = locked;
    form.querySelectorAll("input, select, button").forEach((el) => {
      el.disabled = locked;
    });
    $("recheck").disabled = locked;
    document.querySelectorAll('[name="manualData"]').forEach((el) => {
      el.disabled = locked;
    });
    $("syncNow").disabled = locked || !connected;
    if (!locked) $("syncTime").disabled = $("schedule").value === "off";
  }
  function invalidate() {
    connected = false;
    $("responseTime").textContent = "—";
    status(
      "disconnected",
      "Cấu hình chưa được kiểm tra",
      "Kiểm tra lại kết nối sau khi thay đổi cấu hình.",
    );
  }
  form.addEventListener("input", invalidate);
  form.addEventListener("change", invalidate);
  $("schedule").addEventListener("change", () => {
    $("syncTime").disabled = $("schedule").value === "off";
  });
  $("toggleToken").addEventListener("click", () => {
    const reveal = $("token").type === "password";
    $("token").type = reveal ? "text" : "password";
    $("toggleToken").setAttribute("aria-pressed", String(reveal));
    $("toggleToken").setAttribute("aria-label", reveal ? "Ẩn token" : "Hiện token");
  });
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    if (busy) return;
    const value = validate();
    if (!value) return notify("Kiểm tra các trường được đánh dấu.", true);
    saved = value;
    notify(
      "Đã giữ cấu hình trong phiên giao diện. Chưa lưu lên máy chủ; tải lại trang sẽ xóa cấu hình và token.",
    );
  });
  $("cancel").addEventListener("click", () => {
    if (busy) return;
    form.reset();
    if (saved) {
      const mapping = {
        systemType: "type",
        systemName: "name",
        baseUrl: "url",
        authMethod: "auth",
        token: "token",
        schedule: "schedule",
        syncTime: "time",
      };
      Object.entries(mapping).forEach(([id, key]) => {
        $(id).value = saved[key];
      });
      document.querySelectorAll('[name="configData"]').forEach((el) => {
        el.checked = saved.data.includes(el.value);
      });
    }
    $("token").type = "password";
    $("toggleToken").setAttribute("aria-pressed", "false");
    $("toggleToken").setAttribute("aria-label", "Hiện token");
    $("syncTime").disabled = $("schedule").value === "off";
    ["systemName", "baseUrl", "token", "syncTime", "data"].forEach((id) => {
      $(id + "Error").textContent = "";
      if ($(id)) $(id).removeAttribute("aria-invalid");
    });
    invalidate();
    notify(saved ? "Đã khôi phục cấu hình lưu trong phiên." : "Đã xóa thông tin đang nhập.");
  });
  async function checkConnection() {
    if (busy) return;
    const value = validate();
    if (!value) return notify("Nhập đầy đủ cấu hình trước khi kiểm tra.", true);
    connected = false;
    lock(true);
    status("checking", "Đang kiểm tra kết nối…", "Mô phỏng thao tác kiểm tra trong bản frontend.");
    await wait(900);
    connected = true;
    $("statusType").textContent = value.type;
    $("statusUrl").textContent = value.url;
    $("responseTime").textContent = "120 ms (mẫu)";
    status(
      "connected",
      "Kết nối thành công (mô phỏng)",
      "Chưa xác minh URL hoặc token với hệ thống HRM/ERP thực tế.",
    );
    lock(false);
  }
  $("testConnection").addEventListener("click", checkConnection);
  $("recheck").addEventListener("click", checkConnection);
  $("help").addEventListener("click", () => {
    $("helpText").hidden = !$("helpText").hidden;
  });
  function renderHistory() {
    const from = $("fromDate").value;
    const to = $("toDate").value;
    const filter = $("historyStatus").value;
    const invalid = from && to && from > to;
    $("toDate").setAttribute("aria-invalid", String(Boolean(invalid)));
    if (invalid) notify("Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.", true);
    const rows = invalid
      ? []
      : history.filter(
          (row) =>
            (filter === "all" || row.state === filter) &&
            (!from || dayKey(row.start) >= from) &&
            (!to || dayKey(row.start) <= to),
        );
    $("historyBody").replaceChildren();
    rows.forEach((row, index) => {
      const tr = element("tr");
      [
        index + 1,
        dateText(row.start),
        dateText(row.end),
        row.data.join(", "),
        row.state === "running" ? "—" : new Intl.NumberFormat("vi-VN").format(row.count),
      ].forEach((text) => tr.append(element("td", text)));
      const state = element("td");
      state.append(element("span", labels[row.state], "badge " + row.state));
      tr.append(state, element("td", row.message));
      const action = element("td");
      const button = element("button", "▤");
      button.type = "button";
      button.setAttribute("aria-label", "Xem chi tiết đồng bộ " + dateText(row.start));
      button.addEventListener("click", () => showDetail(row));
      action.append(button);
      tr.append(action);
      $("historyBody").append(tr);
    });
    if (!rows.length) {
      const tr = element("tr");
      const cell = element(
        "td",
        invalid
          ? "Khoảng ngày không hợp lệ."
          : "Chưa có lịch sử phù hợp. Đồng bộ mô phỏng hoặc xem lịch sử mẫu.",
        "empty",
      );
      cell.colSpan = 8;
      tr.append(cell);
      $("historyBody").append(tr);
    }
    $("historyCount").textContent =
      `${rows.length} / ${history.length} lượt đồng bộ trong phiên (mô phỏng)`;
  }
  function showDetail(row) {
    $("detailContent").replaceChildren();
    const details = {
      "Mã đồng bộ": row.id,
      "Bắt đầu": dateText(row.start),
      "Kết thúc": dateText(row.end),
      "Dữ liệu": row.data.join(", "),
      "Số bản ghi mẫu": row.count,
      "Trạng thái": labels[row.state],
      "Thông báo": row.message,
    };
    Object.entries(details).forEach(([key, value]) =>
      $("detailContent").append(element("dt", key), element("dd", value)),
    );
    $("detailDialog").showModal();
  }
  $("closeDetail").addEventListener("click", () => $("detailDialog").close());
  ["historyStatus", "fromDate", "toDate"].forEach((id) =>
    $(id).addEventListener("change", renderHistory),
  );
  $("loadDemo").addEventListener("click", () => {
    if (history.some((row) => row.sample))
      return notify("Lịch sử mẫu đã được thêm; dùng bộ lọc để xem.");
    const now = new Date();
    for (let i = 0; i < 5; i++) {
      const start = new Date(now.getTime() - (i + 1) * 86400000);
      history.push({
        id: `DEMO-${++sequence}`,
        start: start.toISOString(),
        end: new Date(start.getTime() + 60000).toISOString(),
        data: i % 2 ? ["Người dùng"] : ["Người dùng", "Phòng ban"],
        count: i === 3 ? 0 : 1245 - i,
        state: i === 3 ? "failed" : "success",
        message: i === 3 ? "Mẫu lỗi kết nối: Timeout." : "Đồng bộ thành công (dữ liệu mẫu).",
        sample: true,
      });
    }
    history.sort((a, b) => new Date(b.start) - new Date(a.start));
    renderHistory();
    notify("Đã thêm 5 lượt đồng bộ mẫu để xem giao diện lịch sử.");
  });
  $("syncNow").addEventListener("click", async () => {
    if (busy || !connected) return;
    if (!saved || JSON.stringify(saved) !== JSON.stringify(config()))
      return notify("Lưu cấu hình hiện tại trước khi đồng bộ.", true);
    const data = selected("manualData");
    if (!data.length) return notify("Chọn ít nhất một loại dữ liệu để đồng bộ.", true);
    const row = {
      id: `DEMO-${++sequence}`,
      start: new Date().toISOString(),
      end: null,
      data,
      count: 0,
      state: "running",
      message: "Đang xử lý (mô phỏng)…",
    };
    history.unshift(row);
    lock(true);
    $("syncNow").textContent = "⟳ Đang đồng bộ…";
    renderHistory();
    await wait(1600);
    row.end = new Date().toISOString();
    row.count = data.reduce(
      (total, type) =>
        total +
        ({ "Người dùng": 1240, "Phòng ban": 25, "Thông tin tổ chức": 1, "Thông tin phòng họp": 12 }[
          type
        ] || 0),
      0,
    );
    row.state = "success";
    row.message = "Đồng bộ thành công (mô phỏng).";
    $("lastSync").textContent = dateText(row.end) + " (mẫu)";
    $("syncNow").textContent = "⟳ Đồng bộ ngay";
    lock(false);
    renderHistory();
    notify("Đã hoàn tất đồng bộ mô phỏng. Chưa có dữ liệu được gửi hoặc cập nhật trên máy chủ.");
  });
  $("menu").addEventListener("click", () => {
    const mobile = window.matchMedia("(max-width: 800px)").matches;
    document.body.classList.toggle(mobile ? "menu-open" : "menu-closed");
    $("menu").setAttribute(
      "aria-expanded",
      String(
        mobile
          ? document.body.classList.contains("menu-open")
          : !document.body.classList.contains("menu-closed"),
      ),
    );
  });
  renderHistory();
})();
