"use strict";
(() => {
  const $ = (id) => document.getElementById(id);
  const node = (tag, text, cls) => {
    const element = document.createElement(tag);
    if (text != null) element.textContent = text;
    if (cls) element.className = cls;
    return element;
  };
  const paths = {
    equipment: "M3 4h18v13H3z M7 21h10 M12 17v4 M6 8h6 M6 11h9",
    home: "m3 10 9-8 9 8 M5 9v13h5v-8h4v8h5V9",
    plus: "M12 3v18 M3 12h18",
    calendar: "M4 5h16v16H4z M8 2v6 M16 2v6 M4 10h16 M8 14h2 M14 14h2",
    room: "M3 2h12v18H3z M15 9h6v11 M7 7h4 M7 11h4 M7 15h4",
    chart: "M3 3v18h18 M8 16V8 M13 16V5 M18 16v-5",
    settings:
      "M10 2h4l1 4 4 1 2 4-3 3 1 4-4 2-3-3-4 1-2-4 3-3-1-4z M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0",
    search: "M16 16l5 5 M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0",
    bell: "M6 9a6 6 0 0 1 12 0c0 5 3 6 3 8H3c0-2 3-3 3-8 M10 21h4",
    chevron: "m7 9 5 5 5-5",
    menu: "M3 5h18 M3 12h18 M3 19h18",
    info: "M12 10v7 M12 6v1 M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0",
    check: "m4 12 5 5L20 6",
    clock: "M12 6v6l4 2 M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0",
    edit: "m15 3 6 6-12 12H3v-6z M12 6l6 6",
    trash: "M3 6h18 M8 6V3h8v3 M6 6l1 15h10l1-15 M10 10v7 M14 10v7",
    minus: "M5 12h14",
  };
  function icon(type) {
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("viewBox", "0 0 24 24");
    svg.setAttribute("aria-hidden", "true");
    const path = document.createElementNS(svg.namespaceURI, "path");
    path.setAttribute("d", paths[type] || paths.equipment);
    svg.append(path);
    return svg;
  }
  function art(type) {
    const wrap = node("span", null, "device-art");
    wrap.setAttribute("aria-hidden", "true");
    const drawing = {
      projector:
        '<rect x="5" y="16" width="51" height="27" rx="8" fill="#dbe2e9"/><path d="M11 20h19m-19 5h16m-16 5h12" stroke="#8b98a7" stroke-width="2"/><circle cx="43" cy="29" r="10" fill="#192b40"/><circle cx="43" cy="29" r="6" fill="#5a9bd0"/><circle cx="41" cy="27" r="2" fill="#bce8ff"/><path d="M12 43v3m36-3v3" stroke="#253950" stroke-width="3"/>',
      microphone:
        '<path d="m22 45 15-23 8 5-15 24z" fill="#354254"/><ellipse cx="44" cy="18" rx="10" ry="12" transform="rotate(30 44 18)" fill="#687587"/><path d="m38 11 12 8m-15-3 14 8m-17-2 13 7" stroke="#c5ccd5"/><path d="m20 44 10 6-2 4-10-6z" fill="#111e2d"/>',
      laptop:
        '<path d="M12 8h40v31H12z" fill="#172b3e"/><path d="M15 11h34v25H15z" fill="#268ec6"/><path d="m15 36 18-25h16v25" fill="#0a527f"/><path d="M12 39h40l10 10H2z" fill="#aebcc9"/><path d="M5 49h54v3H5z" fill="#687d91"/><path d="m26 46 2-4h8l2 4" fill="#e3eaf0"/>',
      speaker:
        '<rect x="10" y="6" width="44" height="49" rx="5" fill="#253242"/><rect x="13" y="9" width="38" height="43" rx="3" fill="#344355"/><circle cx="32" cy="20" r="8" fill="#111f30"/><circle cx="32" cy="39" r="12" fill="#172337"/><circle cx="32" cy="39" r="6" fill="#52667d"/><circle cx="32" cy="20" r="3" fill="#718499"/>',
      camera:
        '<path d="M29 39h6v10h-6z" fill="#53697e"/><ellipse cx="32" cy="51" rx="21" ry="5" fill="#294155"/><rect x="11" y="9" width="42" height="33" rx="13" fill="#283f52"/><circle cx="32" cy="25" r="14" fill="#11263a"/><circle cx="32" cy="25" r="9" fill="#31698c"/><circle cx="32" cy="25" r="5" fill="#0d2238"/><circle cx="29" cy="22" r="2" fill="#9bd7ef"/>',
    };
    wrap.innerHTML =
      '<svg viewBox="0 0 64 60" xmlns="http://www.w3.org/2000/svg">' +
      (drawing[type] || drawing.projector) +
      "</svg>";
    return wrap;
  }
  document
    .querySelectorAll("[data-icon]")
    .forEach((el) => el.append(icon(el.dataset.icon)));
  const form = $("createMeetingForm");
  const quantities = new Map();
  const uncertain = new Set();
  const receipts = [];
  // Keep API receipts in this browser tab across reloads; this is not server history.
  try {
    const previous = window.TTCSDemo?window.TTCSDemo.state.equipmentBookings.map(b=>({...b,title:window.TTCSDemo.state.meetings.find(m=>m.meetingId===b.meetingId)?.title||b.meetingId,name:window.TTCSDemo.state.equipment.find(e=>e.equipmentId===b.equipmentId)?.equipmentName||b.equipmentId})):JSON.parse(sessionStorage.getItem("us12-api-receipts") || "[]");
    if (Array.isArray(previous)) previous.filter((entry) => entry && typeof entry.id === "string"
      && typeof entry.meetingId === "string" && typeof entry.equipmentId === "string"
      && entry.id === entry.meetingId + ":" + entry.equipmentId
      && typeof entry.title === "string" && typeof entry.name === "string"
      && Number.isInteger(entry.quantity) && entry.quantity > 0
      && Number.isFinite(Date.parse(entry.startTime)) && Number.isFinite(Date.parse(entry.endTime))
      && ["BOOKED", "UNKNOWN", "CANCELLED", "ABSENT"].includes(entry.status)).slice(0, 200)
      .forEach((entry) => { receipts.push(entry); if (entry.status === "UNKNOWN") uncertain.add(entry.id); });
  } catch (_) { /* Storage may be unavailable; retain in-memory receipts. */ }
  let rooms = [], equipment = [], meetings = [], stock = [];
  let loading = true, checking = false, saving = false, showAll = false;
  let version = 0, checkedKey = null, pendingCancel = null, refreshTimer;
  let context = window.US12Context || null;
  const fold = (value) => String(value || "").toLocaleLowerCase("vi")
    .normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/đ/g, "d");
  const selectedMeeting = () => meetings.find((meeting) => meeting.id === $("meetingTitle").value);
  const selectedRoom = () => rooms.find((room) => room.roomId === $("meetingRoom").value);
  const roomUsable = (room) => !!room && ["ACTIVE", "AVAILABLE", "OCCUPIED", "IN_USE"].includes(String(room.status).toUpperCase());
  const bookingKey = (meetingId, equipmentId) => meetingId + ":" + equipmentId;
  const roleNames = () => [context?.role, context?.roleId, context?.role?.roleName, context?.role?.roleId];
  function canBook() {
    return typeof context?.userId === "string" && !!context.userId.trim()
      && (!context.status || context.status === "ACTIVE") && roleNames().some((role) => typeof role === "string"
        && ["ADMIN", "ROLE_ADMIN", "PM", "ROLE_PM", "PROJECT_MANAGER", "ROLE_PROJECT_MANAGER", "PROJECT MANAGER"].includes(role.trim().toUpperCase()));
  }
  function status(text, error = false) {
    $("formStatus").textContent = text;
    $("formStatus").hidden = !text;
    $("formStatus").classList.toggle("error", error);
  }
  function fieldError(id, text = "") {
    const error = $(id + "Error");
    if (error) error.textContent = text;
    $(id)?.setAttribute("aria-invalid", String(!!text));
  }
  function clearErrors() {
    ["meetingTitle", "meetingRoom", "meetingDate", "startTime", "endTime"].forEach((id) => fieldError(id));
    $("equipmentError").textContent = "";
  }
  const fullRange = (meeting = selectedMeeting()) => {
    if (!meeting) return null;
    const start = Date.parse(meeting.startTime), end = Date.parse(meeting.endTime);
    return Number.isFinite(start) && Number.isFinite(end) && end > start ? { start, end } : null;
  };
  const stockKey = () => JSON.stringify([selectedMeeting()?.id, selectedMeeting()?.room, selectedMeeting()?.startTime, selectedMeeting()?.endTime]);
  const isoOffset = (time) => new Date(time + 7 * 3600000).toISOString().slice(0, 23) + "+07:00";
  const displayDate = (time) => new Date(time).toLocaleDateString("vi-VN", { timeZone: "Asia/Ho_Chi_Minh" });
  const displayTime = (time) => new Date(time).toLocaleTimeString("vi-VN", { timeZone: "Asia/Ho_Chi_Minh", hour: "2-digit", minute: "2-digit" });
  const safeQuantity = (item) => quantities.get(item.id) ?? 0;
  const selectedItems = () => equipment.filter((item) => safeQuantity(item) !== 0);
  function quantityIssue(item) {
    const quantity = safeQuantity(item);
    const availability = stock.find((entry) => entry.id === item.id)?.available;
    if (uncertain.has(bookingKey(selectedMeeting()?.id, item.id)))
      return "Chưa rõ kết quả lần gửi trước. Kiểm tra lượt đặt trước khi gửi lại.";
    if (receipts.some((receipt) => receipt.meetingId === selectedMeeting()?.id && receipt.equipmentId === item.id && receipt.status === "BOOKED"))
      return quantity ? "Thiết bị đã được đặt cho cuộc họp này trong phiên hiện tại." : "";
    if (!Number.isInteger(quantity) || quantity < 0) return "Số lượng phải là số nguyên từ 0 trở lên.";
    if (quantity && (!Number.isInteger(availability) || quantity > availability))
      return Number.isInteger(availability) ? `Chỉ còn ${availability}; đang chọn ${quantity}.` : "Chưa kiểm tra được số lượng khả dụng.";
    return availability === 0 ? "Không có thiết bị khả dụng." : "";
  }
  function syncControls() {
    const invalid = selectedItems().some((item) => quantityIssue(item));
    $("submitBtn").disabled = saving || loading || checking || !canBook() || !selectedMeeting()
      || checkedKey !== stockKey() || invalid || !selectedItems().length;
    $("cancelBtn").disabled = saving;
    $("reloadData").disabled = saving || loading;
    $("meetingTitle").disabled = saving || loading;
    $("search").disabled = saving;
    $("permissionStatus").hidden = canBook();
    $("permissionStatus").textContent = context?.userId
      ? "Chỉ Admin và PM được đặt hoặc hủy thiết bị. Bạn có thể xem tình trạng thiết bị."
      : "Chưa nhận được tài khoản từ phiên đăng nhập. Đăng nhập bằng tài khoản Admin hoặc PM để đặt thiết bị.";
    const roles = roleNames().filter((role) => typeof role === "string").map((role) => role.trim().toUpperCase());
    $("currentUserRole").textContent = roles.some((role) => ["ADMIN", "ROLE_ADMIN"].includes(role)) ? "Admin"
      : roles.some((role) => ["PM", "ROLE_PM", "PROJECT_MANAGER", "ROLE_PROJECT_MANAGER", "PROJECT MANAGER"].includes(role)) ? "PM"
      : roles.some((role) => ["MEMBER", "ROLE_MEMBER", "USER", "ROLE_USER"].includes(role)) ? "Member"
      : context?.userId ? "Chưa rõ vai trò" : "Chưa đăng nhập";
    const displayName = typeof context?.fullName === "string" ? context.fullName.trim() : "";
    const userId = typeof context?.userId === "string" ? context.userId.trim() : "";
    $("currentUserName").textContent = displayName || userId || "Chưa đăng nhập";
  }
  function artType(item) {
    const text = fold(item.name + " " + item.description);
    if (/micro|am thanh|microphone/.test(text)) return "microphone";
    if (/laptop|may tinh/.test(text)) return "laptop";
    if (/camera|ghi hinh/.test(text)) return "camera";
    if (/loa|speaker/.test(text)) return "speaker";
    return "projector";
  }
  function renderInventory() {
    const query = fold($("search").value.trim());
    const meeting = selectedMeeting();
    const visible = equipment.filter((item) => item.roomId === meeting?.room && fold(item.name + " " + item.description).includes(query));
    $("equipmentOptions").replaceChildren();
    $("stockList").replaceChildren();
    visible.forEach((item) => {
      const current = stock.find((entry) => entry.id === item.id);
      const available = checkedKey === stockKey() ? current?.available : null;
      const booked = checkedKey === stockKey() ? current?.booked : null;
      const quantity = safeQuantity(item);
      const row = node("tr"); row.dataset.equipmentId = item.id;
      row.classList.toggle("is-empty", available === 0);
      const identity = node("td"), product = node("div", null, "equipment-product");
      product.append(art(artType(item)), node("strong", item.name)); identity.append(product);
      const remaining = node("td"); remaining.append(node("span", available ?? "—", "count-pill" + (available === 0 ? " zero" : "")));
      row.append(identity, node("td", item.description), node("td", item.total), node("td", booked ?? "—"), remaining);
      const action = node("td"), control = node("div", null, "qty-control");
      const locked = saving || !canBook() || checking || checkedKey !== stockKey() || !Number.isInteger(available);
      const input = node("input");
      input.type = "number"; input.id = "quantity-" + item.id; input.min = "0"; input.step = "1";
      if (Number.isInteger(available)) input.max = String(available);
      input.value = Number.isFinite(quantity) ? quantity : "";
      // A previous quantity must remain clearable after stock drops to zero.
      input.disabled = locked;
      input.setAttribute("aria-label", "Số lượng đặt " + item.name);
      input.setAttribute("aria-describedby", "quantity-error-" + item.id);
      input.setAttribute("aria-invalid", String(!!quantity && !!quantityIssue(item)));
      input.addEventListener("input", () => {
        quantities.set(item.id, input.value === "" ? NaN : Number(input.value));
        const error = $("quantity-error-" + item.id);
        error.textContent = quantityIssue(item); error.hidden = !error.textContent;
        input.setAttribute("aria-invalid", String(!!quantityIssue(item) && safeQuantity(item) !== 0));
        syncControls();
      });
      input.addEventListener("change", renderInventory);
      for (const step of [-1, 1]) {
        const button = node("button"); button.type = "button"; button.dataset.step = String(step);
        button.setAttribute("aria-label", (step < 0 ? "Giảm " : "Tăng ") + item.name);
        button.disabled = locked || (step < 0 ? !(quantity > 0) : !Number.isInteger(quantity) || quantity >= available || !!quantityIssue({ ...item }) && quantity > 0);
        button.append(icon(step < 0 ? "minus" : "plus"));
        button.onclick = () => { quantities.set(item.id, Math.max(0, Number(quantity || 0) + step)); status(""); renderInventory(); };
        if (step < 0) control.append(button, input); else control.append(button);
      }
      const error = node("small", quantityIssue(item), "row-error"); error.id = "quantity-error-" + item.id; error.hidden = !error.textContent;
      action.append(control, error); row.append(action); $("equipmentOptions").append(row);
      const stockRow = node("div", null, "stock-item"), text = node("div"), right = node("div", null, "stock-right");
      text.append(node("strong", item.name, "product-name"), node("small", "Tổng: " + item.total, "stock-meta"));
      right.append(node("span", "Còn " + (available ?? "—"), "count-pill" + (available === 0 ? " zero" : "")), node("small", "Đã đặt tối đa: " + (booked ?? "—"), "stock-meta"));
      stockRow.append(art(artType(item)), text, right); $("stockList").append(stockRow);
    });
    if (!visible.length) {
      const row = node("tr"), cell = node("td", meeting ? "Phòng chưa có thiết bị phù hợp trong dữ liệu máy chủ." : "Chọn một cuộc họp đã tạo để xem thiết bị.");
      cell.colSpan = 6; row.append(cell); $("equipmentOptions").append(row);
      $("stockList").append(node("p", cell.textContent, "history-summary"));
    }
    const range = fullRange();
    $("stockRange").textContent = range ? displayDate(range.start) + " | " + displayTime(range.start) + " - " + displayTime(range.end) : "Chưa chọn cuộc họp";
    syncControls();
  }
  async function api(url, options = {}) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 15000);
    try {
      const response = await fetch(url, { ...options, credentials: "same-origin", signal: controller.signal,
        headers: { Accept: "application/json", ...(options.body ? { "Content-Type": "application/json" } : {}) } });
      const text = await response.text(); let body;
      try { body = text ? JSON.parse(text) : null; } catch (_) { body = null; }
      if (!response.ok) {
        const error = Error(typeof body?.message === "string" ? body.message : typeof body?.detail === "string" ? body.detail
          : ({ 401: "Phiên đăng nhập đã hết hạn.", 403: "Bạn không có quyền thực hiện thao tác này.", 409: "Không đủ thiết bị trong khoảng thời gian này." }[response.status] || "Máy chủ trả lỗi HTTP " + response.status));
        error.notFound = response.status === 404 || /404 NOT_FOUND/.test(error.message);
        error.uncertain = options.method === "POST" && response.status >= 500 && !/409 CONFLICT|400 BAD_REQUEST|404 NOT_FOUND/.test(error.message);
        throw error;
      }
      if (text && body === null) {
        const error = Error("Máy chủ trả dữ liệu không hợp lệ."); error.uncertain = options.method === "POST"; throw error;
      }
      return body;
    } catch (error) {
      if (error.name === "AbortError" || error instanceof TypeError) {
        const failure = Error(options.method === "POST"
          ? "Mất kết nối hoặc chưa nhận được kết quả đặt thiết bị. Kiểm tra lượt đặt trước khi gửi lại."
          : "Không kết nối được máy chủ. Vui lòng thử tải lại dữ liệu.");
        failure.uncertain = options.method === "POST"; throw failure;
      }
      throw error;
    } finally { clearTimeout(timeout); }
  }
  async function refreshStock() {
    clearTimeout(refreshTimer);
    const meeting = selectedMeeting(), range = fullRange();
    const requestKey = stockKey(), token = ++version;
    checkedKey = null; stock = []; checking = true;
    renderInventory();
    if (!meeting || !range || !roomUsable(selectedRoom())) {
      checking = false;
      $("availabilityNote").querySelector("span").textContent = "Chọn cuộc họp hợp lệ và phòng đang hoạt động để kiểm tra thiết bị.";
      syncControls(); return false;
    }
    $("availabilityNote").querySelector("span").textContent = "Đang kiểm tra tồn kho trong toàn thời gian cuộc họp…";
    try {
      // All reservations in this backend inherit their meeting interval. Stock
      // can only decrease at a meeting start, so inspect each such boundary.
      const [latestMeetings, latestRoom] = await Promise.all([api("/api/meetings"), api("/api/rooms/" + encodeURIComponent(meeting.room))]);
      if (latestRoom?.roomId !== meeting.room || !roomUsable(latestRoom)) throw Error("Phòng đang bảo trì, ngừng sử dụng hoặc không còn tồn tại.");
      if (!Array.isArray(latestMeetings)) throw Error("Danh sách cuộc họp không hợp lệ.");
      const latest = latestMeetings.find((item) => item.id === meeting.id);
      if (!latest || latest.status !== "SCHEDULED" || latest.room !== meeting.room || latest.startTime !== meeting.startTime || latest.endTime !== meeting.endTime)
        throw Error("Thông tin cuộc họp đã thay đổi. Tải lại danh sách trước khi đặt thiết bị.");
      const checkpoints = [...new Set([range.start, ...latestMeetings.filter((item) => item.status !== "CANCELLED")
        .map((item) => Date.parse(item.startTime)).filter((time) => time > range.start && time < range.end)])].sort((a, b) => a - b);
      const results = new Array(checkpoints.length); let next = 0;
      const worker = async () => {
        while (next < checkpoints.length && token === version) {
          const index = next++;
          results[index] = await api("/api/equipment/status?" + new URLSearchParams({ room: meeting.room, at: isoOffset(checkpoints[index]) }));
        }
      };
      await Promise.all(Array.from({ length: Math.min(4, checkpoints.length) }, worker));
      if (token !== version || requestKey !== stockKey()) return false;
      const inventory = await api("/api/equipment");
      if (token !== version || requestKey !== stockKey()) return false;
      if (!Array.isArray(inventory) || results.some((result) => !Array.isArray(result))) throw Error("Dữ liệu thiết bị không hợp lệ.");
      equipment = inventory.map(mapEquipment);
      stock = equipment.filter((item) => item.roomId === meeting.room).map((item) => {
        const observations = results.map((result) => result.find((entry) => entry.equipmentId === item.id));
        if (observations.some((entry) => !entry || !Number.isInteger(entry.availableQuantity) || entry.availableQuantity < 0
          || !Number.isInteger(entry.bookedQuantity) || entry.bookedQuantity < 0)) throw Error("Thiếu dữ liệu tồn kho; chưa thể xác nhận số lượng.");
        return { id: item.id, available: item.status === "AVAILABLE" ? Math.min(item.total, ...observations.map((entry) => entry.availableQuantity)) : 0,
          booked: Math.max(...observations.map((entry) => entry.bookedQuantity)) };
      });
      checkedKey = requestKey;
      $("availabilityNote").querySelector("span").textContent = `Đã kiểm tra toàn khoảng ${displayTime(range.start)}–${displayTime(range.end)} ngày ${displayDate(range.start)}. Hai lịch chỉ tiếp giáp không bị tính trùng.`;
      return true;
    } catch (error) {
      if (token === version && requestKey === stockKey()) $("availabilityNote").querySelector("span").textContent = error.message;
      return false;
    } finally {
      if (token === version) { checking = false; renderInventory(); }
    }
  }
  function mapEquipment(item) {
    if (typeof item?.equipmentId !== "string" || !Number.isInteger(item.totalQuantity) || item.totalQuantity < 0)
      throw Error("Danh sách thiết bị không hợp lệ.");
    return { id: item.equipmentId, roomId: item.roomId, name: item.equipmentName, description: item.type || "", total: item.totalQuantity, status: String(item.status || "").toUpperCase() };
  }
  function selectMeeting() {
    version++; checkedKey = null; checking = false; quantities.clear(); clearErrors();
    const meeting = selectedMeeting(), range = fullRange(meeting);
    $("meetingRoom").value = meeting?.room || "";
    $("meetingNotes").value = meeting?.description || "";
    const localStart = range ? isoOffset(range.start) : "", localEnd = range ? isoOffset(range.end) : "";
    $("meetingDate").value = localStart.slice(0, 10);
    $("startTime").value = localStart.slice(11, 16);
    $("endTime").value = localEnd.slice(11, 16);
    $("descriptionCount").textContent = $("meetingNotes").value.length + "/500";
    renderInventory();
    clearTimeout(refreshTimer);
    if (meeting) refreshTimer = setTimeout(refreshStock, 100);
  }
  async function loadCatalog() {
    loading = true; syncControls(); status("Đang tải cuộc họp, phòng và thiết bị từ máy chủ…");
    const previous = $("meetingTitle").value;
    try {
      const result = await Promise.all([api("/api/rooms"), api("/api/equipment"), api("/api/meetings")]);
      if (result.some((value) => !Array.isArray(value))) throw Error("Dữ liệu danh sách từ máy chủ không hợp lệ.");
      [rooms, , meetings] = result; equipment = result[1].map(mapEquipment);
      $("meetingRoom").replaceChildren(new Option("Phòng của cuộc họp", ""));
      rooms.forEach((room) => $("meetingRoom").append(new Option(`${room.roomName} (${room.capacity} người)`, room.roomId)));
      $("meetingTitle").replaceChildren(new Option("Chọn cuộc họp đã tạo", ""));
      meetings.filter((meeting) => meeting.status === "SCHEDULED" && fullRange(meeting)?.start > Date.now()).forEach((meeting) => {
        const option = new Option(meeting.title + " · " + displayDate(Date.parse(meeting.startTime)) + " " + displayTime(Date.parse(meeting.startTime)), meeting.id);
        option.disabled = !roomUsable(rooms.find((room) => room.roomId === meeting.room));
        $("meetingTitle").append(option);
      });
      if ([...$("meetingTitle").options].some((option) => option.value === previous && !option.disabled)) $("meetingTitle").value = previous;
      selectMeeting();
      status($("meetingTitle").options.length > 1 ? "Đã tải dữ liệu từ máy chủ. Chọn cuộc họp cần đặt thiết bị."
        : "Chưa có cuộc họp sắp tới. Tạo cuộc họp tại US01 trước khi đặt thiết bị.", $("meetingTitle").options.length === 1);
    } catch (error) { status(error.message, true); }
    finally { loading = false; renderInventory(); }
  }
  function renderHistory() {
    try { sessionStorage.setItem("us12-api-receipts", JSON.stringify(receipts.slice(0, 200))); } catch (_) { /* Keep in memory. */ }
    const query = fold($("search").value.trim());
    const all = receipts.filter((entry) => fold(entry.title + " " + entry.name).includes(query));
    $("bookingHistory").replaceChildren();
    (showAll ? all : all.slice(0, 5)).forEach((entry) => {
      const row = node("tr"); row.dataset.bookingId = entry.id;
      const day = node("td"); day.append(node("span", displayDate(Date.parse(entry.startTime))), node("small", displayTime(Date.parse(entry.startTime)) + " - " + displayTime(Date.parse(entry.endTime))));
      const state = node("td"); state.append(node("span", entry.status === "CANCELLED" ? "Đã hủy" : entry.status === "ABSENT" ? "Không tồn tại" : entry.status === "UNKNOWN" ? "Chưa xác nhận" : "Đã đặt", "status-label" + (entry.status !== "BOOKED" ? " cancelled" : "")));
      const actions = node("td", null, "history-actions");
      const edit = node("button"); edit.type = "button"; edit.disabled = true; edit.title = "API hiện chưa hỗ trợ sửa lượt đặt thiết bị."; edit.setAttribute("aria-label", "Chưa hỗ trợ sửa " + entry.name); edit.append(icon("edit"));
      const cancel = node("button"); cancel.type = "button"; cancel.disabled = !canBook() || saving || ["CANCELLED", "ABSENT"].includes(entry.status);
      cancel.setAttribute("aria-label", "Hủy " + entry.name); cancel.append(icon("trash"));
      cancel.onclick = () => { pendingCancel = entry; $("cancel-message").textContent = `Hủy đặt ${entry.quantity} ${entry.name} cho “${entry.title}”? Thao tác sẽ gửi đến máy chủ.`; $("cancel-dialog").showModal(); };
      if(window.TTCSDemo){edit.disabled=!canBook()||saving||entry.status!=='BOOKED';edit.title='Sửa số lượng đặt trong API demo';edit.onclick=async()=>{const raw=prompt('Số lượng thiết bị cần đặt:',String(entry.quantity));if(raw===null)return;const quantity=Number(raw);if(!Number.isInteger(quantity)||quantity<1){status('Số lượng phải là số nguyên dương.',true);return;}try{const saved=await api('/api/equipment/bookings/'+encodeURIComponent(entry.id),{method:'PUT',body:JSON.stringify({quantity})});entry.quantity=saved.quantity;await refreshStock();renderHistory();status('Đã cập nhật số lượng trên API demo.');}catch(error){status(error.message,true)}};}
      actions.append(edit, cancel);
      row.append(day, node("td", entry.title), node("td", entry.name), node("td", entry.quantity), state, actions);
      $("bookingHistory").append(row);
    });
    $("historySummary").textContent = window.TTCSDemo?`Có ${all.length} lượt đặt trong dữ liệu API demo chung.`:all.length ? `Có ${all.length} kết quả gửi trong phiên này. Máy chủ chưa có API lấy toàn bộ lịch sử.`
      : "Chưa có lượt đặt trong phiên này. Máy chủ chưa có API lấy toàn bộ lịch sử.";
    $("showAllHistory").hidden = all.length <= 5;
    $("showAllHistory").textContent = showAll ? "Thu gọn ↑" : "Xem tất cả →";
  }
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (saving || loading) return;
    if (!canBook()) { status($("permissionStatus").textContent, true); return; }
    clearErrors();
    const meeting = selectedMeeting(), range = fullRange();
    if (!meeting || meeting.status !== "SCHEDULED" || !range || range.start <= Date.now()) {
      fieldError("meetingTitle", "Chọn cuộc họp sắp tới hợp lệ."); status("Vui lòng kiểm tra cuộc họp đã chọn.", true); return;
    }
    if (!roomUsable(selectedRoom())) { fieldError("meetingRoom", "Phòng không hoạt động hoặc không tồn tại."); return; }
    const requestKey = stockKey();
    saving = true; syncControls(); renderHistory();
    let saved = 0, failed = false;
    try {
      if (!await refreshStock() || stockKey() !== requestKey) throw Error("Chưa kiểm tra được tồn kho. Không gửi yêu cầu đặt.");
      const selected = selectedItems();
      if (!selected.length) throw Error("Chọn ít nhất một thiết bị với số lượng lớn hơn 0.");
      const issue = selected.map(quantityIssue).find(Boolean);
      if (issue) { $("equipmentError").textContent = issue; throw Error(issue); }
      for (const item of selected) {
        const quantity = safeQuantity(item), id = bookingKey(meeting.id, item.id);
        const request = { equipmentId: item.id, meetingId: meeting.id, room: meeting.room, quantity,
          startTime: meeting.startTime, endTime: meeting.endTime };
        try {
          const result = await api("/api/equipment/bookings", { method: "POST", body: JSON.stringify(request) });
          if (!result || result.status !== "BOOKED" || result.id !== id || result.meetingId !== meeting.id || result.equipmentId !== item.id || result.quantity !== quantity) {
            const error = Error("Phản hồi đặt thiết bị chưa đầy đủ. Kiểm tra lượt đặt trước khi gửi lại."); error.uncertain = true; throw error;
          }
          receipts.unshift({ ...result, title: meeting.title, name: item.name });
          quantities.set(item.id, 0); saved++;
        } catch (error) {
          if (error.uncertain) {
            uncertain.add(id);
            receipts.unshift({ id, ...request, title: meeting.title, name: item.name, status: "UNKNOWN" });
          }
          failed = true;
          status((saved ? `Đã lưu ${saved} loại thiết bị; các loại còn lại chưa được xác nhận. ` : "") + error.message, true);
          break;
        }
      }
      if (!failed) status(`Máy chủ đã xác nhận đặt ${saved} loại thiết bị cho “${meeting.title}”.`);
      await refreshStock();
    } catch (error) { status(error.message, true); }
    finally { saving = false; renderInventory(); renderHistory(); }
  });
  $("confirm-cancel").onclick = async () => {
    if (!pendingCancel || saving || !canBook()) return;
    const entry = pendingCancel;
    saving = true; $("confirm-cancel").disabled = true; $("keep-booking").disabled = true; syncControls();
    try {
      await api("/api/equipment/bookings/" + encodeURIComponent(entry.id), { method: "DELETE" });
      entry.status = "CANCELLED"; uncertain.delete(entry.id);
      if (entry.meetingId === selectedMeeting()?.id) quantities.set(entry.equipmentId, 0);
      $("cancel-dialog").close(); pendingCancel = null;
      status("Máy chủ đã xác nhận hủy lượt đặt thiết bị."); await refreshStock();
    } catch (error) {
      if (entry.status === "UNKNOWN" && error.notFound) {
        entry.status = "ABSENT"; uncertain.delete(entry.id);
        if (entry.meetingId === selectedMeeting()?.id) quantities.set(entry.equipmentId, 0);
        $("cancel-dialog").close(); pendingCancel = null;
        status("Máy chủ xác nhận không tồn tại lượt đặt này. Có thể chọn số lượng và gửi lại.");
        await refreshStock();
      } else status(error.message, true);
    }
    finally { saving = false; $("confirm-cancel").disabled = false; $("keep-booking").disabled = false; renderInventory(); renderHistory(); }
  };
  $("keep-booking").onclick = () => { pendingCancel = null; $("cancel-dialog").close(); };
  $("cancel-dialog").addEventListener("cancel", (event) => { if (saving) event.preventDefault(); });
  $("meetingTitle").addEventListener("change", () => { if (!saving) { status(""); selectMeeting(); } });
  $("cancelBtn").onclick = () => { if (!saving) { quantities.clear(); clearErrors(); renderInventory(); status("Đã bỏ số lượng đang chọn; các lượt đã lưu trên máy chủ giữ nguyên."); } };
  $("reloadData").onclick = loadCatalog;
  $("search").addEventListener("input", () => { renderInventory(); renderHistory(); });
  $("showAllHistory").onclick = () => { showAll = !showAll; renderHistory(); };
  $("notifications").onclick = () => status("Chưa có kết nối API thông báo.");
  $("menu-toggle").onclick = () => $("menu-toggle").setAttribute("aria-expanded", String(document.body.classList.toggle("menu-open")));
  window.US12API = {
    setContext(value) { if (!saving) { context = value; renderInventory(); renderHistory(); } },
    refresh: loadCatalog,
  };
  renderHistory(); renderInventory(); loadCatalog();
})();
