"use strict";
(() => {
  const $ = (id) => document.getElementById(id);
  const vietnamOffset = 7 * 60 * 60 * 1000;
  const today = () =>
    new Date(Date.now() + vietnamOffset).toISOString().slice(0, 10);
  const dateLabel = (date) => date.split("-").reverse().join("/");
  const fold = (value) =>
    String(value ?? "")
      .trim()
      .toLocaleLowerCase("vi")
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/đ/g, "d");
  let inventory = [],
    stock = [],
    selectedId = null,
    detailDismissed = false;
  let page = 1,
    loading = false,
    loaded = false,
    failure = "",
    sequence = 0;
  let controller = null,
    debounce = null;
  const node = (tag, text, cls) => {
    const element = document.createElement(tag);
    if (text != null) element.textContent = text;
    if (cls) element.className = cls;
    return element;
  };
  function range(date, startTime, endTime) {
    if (
      !/^\d{4}-\d{2}-\d{2}$/.test(date) ||
      ![startTime, endTime].every((time) =>
        /^([01]\d|2[0-3]):[0-5]\d$/.test(time),
      )
    )
      return null;
    const start = Date.parse(date + "T" + startTime + ":00+07:00");
    const end = Date.parse(date + "T" + endTime + ":00+07:00");
    if (
      !Number.isFinite(start) ||
      !Number.isFinite(end) ||
      end <= start ||
      new Date(start + vietnamOffset).toISOString().slice(0, 10) !== date
    )
      return null;
    return { date, startTime, endTime, start, end };
  }
  const query = () =>
    range($("statusDate").value, $("statusStart").value, $("statusEnd").value);
  function message(text, error = false) {
    $("message").textContent = text;
    $("message").hidden = !text;
    $("message").classList.toggle("error", error);
  }
  function apiError(error) {
    return error.name === "AbortError"
      ? "Yêu cầu API quá thời gian chờ. Hãy kiểm tra lại kết nối."
      : error.message;
  }
  async function getJSON(url, signal) {
    const response = await fetch(url, {
      signal,
      credentials: "same-origin",
      cache: "no-store",
      headers: { Accept: "application/json" },
    });
    let data;
    try {
      data = await response.json();
    } catch {
      throw Error("API không trả JSON hợp lệ (HTTP " + response.status + ").");
    }
    if (!response.ok)
      throw Error(
        data.message ||
          data.error ||
          "API trả lỗi HTTP " + response.status + ".",
      );
    if (!Array.isArray(data))
      throw Error("Dữ liệu API không đúng định dạng danh sách.");
    return data;
  }
  const count = (value) => Number.isSafeInteger(value) && value >= 0;
  function mapEquipment(records) {
    const ids = new Set();
    return records.map((record) => {
      if (
        !record ||
        typeof record.equipmentId !== "string" ||
        !record.equipmentId.trim() ||
        typeof record.equipmentName !== "string" ||
        !record.equipmentName.trim() ||
        typeof record.type !== "string" ||
        !count(record.totalQuantity) ||
        ids.has(record.equipmentId)
      ) {
        throw Error(
          "Danh mục API có thiết bị thiếu thông tin, trùng ID hoặc số lượng không hợp lệ.",
        );
      }
      ids.add(record.equipmentId);
      const rawStatus = String(record.status || "UNKNOWN").toUpperCase();
      const roomId = record.roomId?.trim() || null;
      return {
        id: record.equipmentId,
        code: record.equipmentId,
        name: record.equipmentName,
        type: record.type,
        total: record.totalQuantity,
        roomId,
        location: roomId || "Chưa gán phòng",
        rawStatus,
        status:
          rawStatus === "AVAILABLE"
            ? "ACTIVE"
            : rawStatus === "MAINTENANCE"
              ? "MAINTENANCE"
              : rawStatus,
        condition:
          {
            AVAILABLE: "Đang hoạt động",
            MAINTENANCE: "Bảo trì",
            BROKEN: "Hỏng",
            BOOKED: "Đã đặt",
            IN_USE: "Đang sử dụng",
            INACTIVE: "Ngừng sử dụng",
          }[rawStatus] || rawStatus,
      };
    });
  }
  const unknown = (item, reason = "Chưa kiểm tra") => ({
    ...item,
    booked: null,
    maintenance: null,
    available: null,
    usable: null,
    hasMaintenance: false,
    reason,
  });
  function mergeStatus(item, record) {
    if (
      !record ||
      record.equipmentId !== item.id ||
      typeof record.room !== "string" ||
      record.room.toLowerCase() !== item.roomId?.toLowerCase() ||
      record.totalQuantity !== item.total ||
      ![
        record.bookedQuantity,
        record.maintenanceQuantity,
        record.availableQuantity,
      ].every(count) ||
      record.bookedQuantity +
        record.maintenanceQuantity +
        record.availableQuantity !==
        item.total
    ) {
      return unknown(item, "API chưa trả số lượng hợp lệ cho thiết bị này.");
    }
    return {
      ...item,
      booked: record.bookedQuantity,
      maintenance: record.maintenanceQuantity,
      available: item.status === "ACTIVE" ? record.availableQuantity : 0,
      usable:
        item.status === "ACTIVE"
          ? Math.max(0, item.total - record.maintenanceQuantity)
          : 0,
      hasMaintenance: record.maintenanceQuantity > 0,
      reason: "",
    };
  }
  const shapes = {
    cube: "m12 2 10 5.5v10L12 23 2 17.5v-10z M2 7.5l10 5.5 10-5.5 M12 13v10",
    search: "M16 16l5 5 M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0",
    home: "m3 10 9-8 9 8 M5 9v13h5v-8h4v8h5V9",
    plus: "M12 3v18 M3 12h18",
    calendar: "M4 5h16v16H4z M8 2v6 M16 2v6 M4 10h16 M8 14h2 M14 14h2",
    room: "M3 3h12v18H3z M15 11h6v10 M7 7h4 M7 11h4 M7 15h4",
    equipment: "M3 4h18v13H3z M7 21h10 M12 17v4",
    status: "M4 3h16v18H4z M8 8h8 M8 12h2 M14 12h2 M8 16h8",
    chart: "M3 3v18h18 M8 16V8 M13 16V5 M18 16v-5",
    settings:
      "M10 2h4l1 4 4 1 2 4-3 3 1 4-4 2-3-3-4 1-2-4 3-3-1-4z M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0",
    bell: "M6 9a6 6 0 0 1 12 0c0 5 3 6 3 8H3c0-2 3-3 3-8 M10 21h4",
    chevron: "m7 9 5 5 5-5",
    menu: "M3 5h18 M3 12h18 M3 19h18",
    check: "m4 12 5 5L20 6",
    clock: "M12 6v6l4 2 M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0",
    close: "m6 6 12 12 M18 6 6 18",
    refresh: "M20 7a9 9 0 1 0 1 9 M20 2v6h-6",
    filter: "M3 4h18l-7 8v8l-4-2v-6z",
    eye: "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12 M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0",
  };
  function icon(type) {
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("viewBox", "0 0 24 24");
    svg.setAttribute("fill", "none");
    svg.setAttribute("stroke", "currentColor");
    svg.setAttribute("stroke-width", "1.7");
    svg.setAttribute("stroke-linecap", "round");
    svg.setAttribute("stroke-linejoin", "round");
    svg.setAttribute("aria-hidden", "true");
    const p = document.createElementNS(svg.namespaceURI, "path");
    p.setAttribute("d", shapes[type] || shapes.cube);
    svg.append(p);
    return svg;
  }
  const drawings = {
    projector:
      '<rect x="4" y="15" width="55" height="30" rx="8" fill="#dce2e8" stroke="#b5bec8"/><path d="M10 21h19m-19 5h17m-17 5h17m-17 5h15" stroke="#8b98a7"/><circle cx="44" cy="30" r="12" fill="#16283a"/><circle cx="44" cy="30" r="8" fill="#336e91"/><circle cx="44" cy="30" r="4" fill="#102337"/><circle cx="41" cy="27" r="2" fill="#c4f0ff"/>',
    microphone:
      '<path d="m16 50 23-29 8 7-23 29z" fill="#1c2838"/><ellipse cx="45" cy="17" rx="11" ry="13" transform="rotate(34 45 17)" fill="#657080"/><path d="m39 8 15 10m-18-3 16 10m-20-4 14 10" stroke="#cbd2da"/><path d="m18 47 9 7" stroke="#9aadb9" stroke-width="2"/>',
    laptop:
      '<rect x="12" y="5" width="42" height="33" rx="2" fill="#1a2939"/><path d="M15 8h36v27H15z" fill="#178ec3"/><path d="m15 35 22-27h14v27" fill="#0b4c76"/><path d="M12 38h42l9 11H2z" fill="#b6c3cf" stroke="#778a9d"/><path d="M3 49h59v3H3z" fill="#53677b"/><path d="m25 45 2-4h13l2 4" fill="#d6e0e8"/>',
    speaker:
      '<rect x="5" y="5" width="24" height="51" rx="3" fill="#202c3a"/><rect x="34" y="5" width="24" height="51" rx="3" fill="#293848"/><circle cx="17" cy="18" r="7" fill="#536375"/><circle cx="17" cy="40" r="9" fill="#101e2f" stroke="#647489"/><circle cx="46" cy="18" r="7" fill="#101e2f" stroke="#647489"/><circle cx="46" cy="40" r="9" fill="#52647a"/>',
    camera:
      '<path d="M29 37h6v11h-6z" fill="#53697e"/><ellipse cx="32" cy="51" rx="21" ry="5" fill="#294155"/><rect x="10" y="7" width="44" height="34" rx="14" fill="#283f52"/><circle cx="32" cy="24" r="15" fill="#11263a"/><circle cx="32" cy="24" r="10" fill="#31698c"/><circle cx="32" cy="24" r="6" fill="#0d2238"/><circle cx="29" cy="21" r="2" fill="#b1e8ff"/>',
    display:
      '<rect x="3" y="5" width="58" height="39" rx="2" fill="#172a3d"/><rect x="7" y="9" width="50" height="31" fill="#158ebf"/><path d="m7 40 28-31h22v31" fill="#0e5e8b"/><path d="M29 44h6v9h-6z M20 53h24v3H20z" fill="#627d90"/>',
    whiteboard:
      '<rect x="8" y="4" width="48" height="43" rx="2" fill="#4c6f9b"/><rect x="10" y="6" width="44" height="39" fill="#f7fbff"/><path d="M13 47v9m38-9v9 M9 56h9m28 0h10" stroke="#6d849b" stroke-width="3"/>',
    presenter:
      '<path d="m11 43 29-35q6-7 13-1l4 4q5 4 0 11L27 56q-5 5-10 1l-6-5q-4-4 0-9z" fill="#1f2a3b" stroke="#526277"/><circle cx="44" cy="18" r="4" fill="#7c8b9b"/><path d="m30 31 8-9 5 4-8 9z" fill="#48586b"/><circle cx="26" cy="41" r="2" fill="#88a0b4"/>',
  };

  function thumbnail(item, big = false) {
    const text = fold(item.name + " " + item.type);
    const key = /projector|may chieu/.test(text)
      ? "projector"
      : /micro|mic\b/.test(text)
        ? "microphone"
        : /laptop|may tinh/.test(text)
          ? "laptop"
          : /speaker|loa/.test(text)
            ? "speaker"
            : /camera/.test(text)
              ? "camera"
              : /display|tivi|man hinh/.test(text)
                ? "display"
                : /board|bang/.test(text)
                  ? "whiteboard"
                  : /presenter|but trinh/.test(text)
                    ? "presenter"
                    : null;
    const wrapper = node("span", null, "device-thumb" + (big ? " big" : ""));
    wrapper.setAttribute("aria-hidden", "true");
    if (key)
      wrapper.innerHTML =
        '<svg viewBox="0 0 64 60" xmlns="http://www.w3.org/2000/svg">' +
        drawings[key] +
        "</svg>";
    else wrapper.append(icon("cube"));
    return wrapper;
  }
  document
    .querySelectorAll("[data-icon]")
    .forEach((element) => element.append(icon(element.dataset.icon)));
  function state(item) {
    if (item.rawStatus === "BROKEN") return "broken";
    if (item.rawStatus === "BOOKED") return "reserved";
    if (item.rawStatus === "IN_USE") return "inuse";
    if (item.status === "INACTIVE") return "inactive";
    if (item.status === "MAINTENANCE") return "maintenance";
    if (item.available === null) return "unknown";
    if (item.total > 0 && item.maintenance >= item.total) return "maintenance";
    if (item.available === 0) return "unavailable";
    return item.available === 1 ? "low" : "available";
  }
  const stateNames = {
    available: "Còn trống",
    low: "Sắp hết",
    unavailable: "Hết thiết bị",
    maintenance: "Bảo trì",
    inactive: "Ngừng sử dụng",
    broken: "Hỏng",
    reserved: "Đã đặt",
    inuse: "Đang sử dụng",
    unknown: "Chưa xác định",
  };
  function badge(item) {
    const label = node(
      "span",
      stateNames[state(item)],
      "status-badge state-" + state(item),
    );
    if (item.reason) label.title = item.reason;
    return label;
  }
  function renderStats() {
    const complete =
      loaded &&
      !loading &&
      query() &&
      stock.every((item) => item.available !== null);
    const setStat = (id, value) => {
      $(id).textContent = value === null ? "—" : value;
    };
    setStat(
      "totalCount",
      loaded ? inventory.reduce((sum, item) => sum + item.total, 0) : null,
    );
    $("totalHint").textContent = loaded
      ? inventory.length + " loại thiết bị từ API"
      : "Chưa tải được danh mục";
    for (const [id, field] of [
      ["availableCount", "available"],
      ["bookedCount", "booked"],
      ["blockedCount", "maintenance"],
    ]) {
      setStat(
        id,
        complete ? stock.reduce((sum, item) => sum + item[field], 0) : null,
      );
    }
  }
  function matches(item) {
    const text = fold(
      item.name + " " + item.code + " " + item.type + " " + item.location,
    );
    const status = $("stateFilter").value;
    return (
      (!$("typeFilter").value || item.type === $("typeFilter").value) &&
      (!$("locationFilter").value ||
        item.location === $("locationFilter").value) &&
      (!$("conditionFilter").value ||
        item.status === $("conditionFilter").value) &&
      text.includes(fold($("equipmentSearch").value)) &&
      text.includes(fold($("globalSearch").value)) &&
      (!status ||
        (status === "available"
          ? item.available > 0
          : status === "maintenance"
            ? item.status === "MAINTENANCE" || item.hasMaintenance
            : state(item) === status))
    );
  }
  function renderTable() {
    const filtered = stock.filter(matches),
      size = Number($("pageSize").value);
    const pages = Math.max(1, Math.ceil(filtered.length / size));
    page = Math.max(1, Math.min(page, pages));
    const start = (page - 1) * size;
    $("equipmentTable").replaceChildren();
    for (const [offset, item] of filtered
      .slice(start, start + size)
      .entries()) {
      const row = node("tr");
      row.dataset.equipmentId = item.id;
      row.classList.toggle("selected", item.id === selectedId);
      const identity = node("td"),
        product = node("div", null, "product");
      product.append(thumbnail(item), node("strong", item.name, "device-name"));
      identity.append(product);
      row.append(
        node("td", start + offset + 1),
        identity,
        node("td", item.type),
        node("td", item.total),
        node("td", item.booked ?? "—"),
        node("td", item.available ?? "—"),
      );
      const status = node("td"),
        action = node("td"),
        button = node("button", null, "detail-button");
      status.append(badge(item));
      button.type = "button";
      button.setAttribute("aria-label", "Xem chi tiết " + item.name);
      button.append(icon("eye"), document.createTextNode("Xem chi tiết"));
      button.onclick = () => {
        selectedId = item.id;
        detailDismissed = false;
        render();
      };
      action.append(button);
      row.append(status, action);
      $("equipmentTable").append(row);
    }
    $("emptyState").hidden = filtered.length > 0;
    $("emptyState").textContent = loading
      ? "Đang tải dữ liệu từ API…"
      : !loaded
        ? "Chưa tải được dữ liệu thiết bị. Hãy kiểm tra kết nối và thử lại."
        : !inventory.length
          ? "API chưa có thiết bị. Không sử dụng dữ liệu mẫu thay cho dữ liệu hệ thống."
          : "Không tìm thấy thiết bị phù hợp. Hãy thay đổi bộ lọc.";
    $("resultCount").textContent = filtered.length
      ? "Hiển thị " +
        (start + 1) +
        " - " +
        Math.min(start + size, filtered.length) +
        " / " +
        filtered.length +
        " loại thiết bị"
      : "0 loại thiết bị";
    $("paginationPages").replaceChildren();
    function pageButton(label, target, disabled, current = false) {
      const button = node(
        "button",
        label,
        "page-button" + (current ? " current" : ""),
      );
      button.type = "button";
      button.disabled = disabled;
      button.setAttribute(
        "aria-label",
        typeof label === "number"
          ? "Trang " + label
          : label === "‹"
            ? "Trang trước"
            : "Trang sau",
      );
      if (current) button.setAttribute("aria-current", "page");
      button.onclick = () => {
        page = target;
        render();
      };
      $("paginationPages").append(button);
    }
    pageButton("‹", page - 1, page === 1);
    for (let number = 1; number <= pages; number++)
      pageButton(number, number, false, number === page);
    pageButton("›", page + 1, page === pages);
  }
  function renderDetail() {
    const item = stock.find((entry) => entry.id === selectedId);
    $("detailPanel").hidden = !item;
    document
      .querySelector(".dashboard-grid")
      .classList.toggle("detail-closed", !item);
    if (!item) return;
    const title = node("div", null, "detail-title");
    title.append(node("h3", item.name), badge(item));
    $("detailHero").replaceChildren(thumbnail(item, true), title);
    $("detailFields").replaceChildren();
    for (const [label, value] of [
      ["Mã thiết bị", item.code],
      ["Loại thiết bị", item.type],
      ["Tổng số lượng", item.total],
      ["Số lượng đã đặt", item.booked ?? "—"],
      ["Không khả dụng", item.maintenance ?? "—"],
      ["Số lượng còn lại", item.available ?? "—"],
      ["Phòng lưu trữ (ID)", item.location],
      ["Tình trạng", item.condition],
      ["Kết quả tra cứu", item.reason || "Tại giờ bắt đầu đã chọn"],
    ]) {
      $("detailFields").append(node("dt", label), node("dd", value));
    }
    $("deviceHistory").replaceChildren();
    $("showAllHistory").hidden = true;
    $('historyEmpty').hidden = false;
if(globalThis.TTCSDemo){const rows=globalThis.TTCSDemo.state.equipmentBookings.filter(b=>b.equipmentId===item.id);for(const b of rows){const m=globalThis.TTCSDemo.state.meetings.find(m=>m.meetingId===b.meetingId);const row=node('tr');for(const text of [b.startTime.slice(0,10).split('-').reverse().join('/'),b.startTime.slice(11,16)+' - '+b.endTime.slice(11,16),m?.title||b.meetingId,b.quantity,b.status==='CANCELLED'||m?.status==='CANCELLED'?'Đã hủy':'Đã đặt'])row.append(node('td',text));$('deviceHistory').append(row)}$('historyEmpty').hidden=rows.length>0;$('historyEmpty').textContent='Chưa có lịch sử đặt thiết bị này trong API demo.';}
  }
  function render() {
    const selected = query();
    $("filterError").hidden = Boolean(selected);
    $("filterError").textContent = selected
      ? ""
      : "Chọn ngày hợp lệ và giờ kết thúc sau giờ bắt đầu.";
    $("scopeNotice").hidden = !selected;
    $("scopeNotice").textContent = selected
      ? "API hiện tại chỉ xác nhận trạng thái tại " +
        selected.startTime +
        " ngày " +
        dateLabel(selected.date) +
        ". Chưa thể xác nhận số lượng còn lại suốt khoảng " +
        selected.startTime +
        "–" +
        selected.endTime +
        "."
      : "";
    $("statusFilters").setAttribute("aria-busy", String(loading));
    $("checkStatus").replaceChildren(
      icon("search"),
      document.createTextNode(
        loading ? "Đang kiểm tra…" : "Kiểm tra trạng thái",
      ),
    );
    $("rangeNote").textContent = selected
      ? "Thời gian tra cứu theo múi giờ Việt Nam (UTC+07:00). " +
        "Danh mục và số lượng lấy từ API; tự làm mới mỗi 30 giây khi trang đang hiển thị."
      : "Chưa có thời gian tra cứu hợp lệ.";
    renderStats();
    renderTable();
    renderDetail();
  }
  function updateOptions(id, values, placeholder) {
    const value = $(id).value;
    $(id).replaceChildren(new Option(placeholder, ""));
    for (const option of [...new Set(values)].sort((a, b) =>
      a.localeCompare(b, "vi"),
    ))
      $(id).append(new Option(option, option));
    $(id).value = values.includes(value) ? value : "";
  }
  async function refresh() {
    clearTimeout(debounce);
    controller?.abort();
    const requestId = ++sequence,
      selected = query();
    stock = inventory.map((item) => unknown(item));
    failure = "";
    if (!selected) {
      loading = false;
      message("");
      render();
      return;
    }
    if (location.protocol === "file:") {
      loaded = false;
      inventory = [];
      stock = [];
      loading = false;
      message(
        "Hãy mở http://localhost:8080 để sử dụng API. Không mở trực tiếp file HTML.",
        true,
      );
      render();
      return;
    }
    controller = new AbortController();
    const signal = controller.signal;
    const timeout = setTimeout(
      () => controller && requestId === sequence && controller.abort(),
      15000,
    );
    let catalogReceived = false;
    loading = true;
    message("Đang tải danh mục và trạng thái từ API…");
    render();
    try {
      const data = await getJSON("/api/equipment", signal);
      const equipment = mapEquipment(data);
      if (requestId !== sequence) return;
      catalogReceived = true;
      inventory = equipment;
      stock = inventory.map((item) => unknown(item, "Đang tải trạng thái…"));
      loaded = true;
      updateOptions(
        "typeFilter",
        inventory.map((item) => item.type),
        "Tất cả loại thiết bị",
      );
      updateOptions(
        "locationFilter",
        inventory.map((item) => item.location),
        "Tất cả vị trí",
      );
      if (!inventory.some((item) => item.id === selectedId))
        selectedId = detailDismissed ? null : inventory[0]?.id || null;
      render();
      const rooms = [
        ...new Set(inventory.map((item) => item.roomId).filter(Boolean)),
      ];
      const statusById = new Map(),
        errors = [];
      let nextRoom = 0;
      async function worker() {
        while (nextRoom < rooms.length && !signal.aborted) {
          const room = rooms[nextRoom++];
          try {
            const params = new URLSearchParams({
              room,
              at: selected.date + "T" + selected.startTime + ":00+07:00",
            });
            const rows = await getJSON(
              "/api/equipment/status?" + params,
              signal,
            );
            const ids = new Set();
            for (const row of rows) {
              if (
                !row ||
                typeof row.equipmentId !== "string" ||
                ids.has(row.equipmentId) ||
                typeof row.room !== "string" ||
                row.room.toLowerCase() !== room.toLowerCase()
              ) {
                throw Error("API trạng thái trả sai phòng hoặc trùng ID.");
              }
              ids.add(row.equipmentId);
            }
            for (const row of rows) statusById.set(row.equipmentId, row);
          } catch (error) {
            if (signal.aborted) throw error;
            errors.push(room + ": " + apiError(error));
          }
        }
      }
      await Promise.all(
        Array.from({ length: Math.min(4, rooms.length) }, worker),
      );
      if (requestId !== sequence) return;
      stock = inventory.map((item) =>
        item.roomId
          ? mergeStatus(item, statusById.get(item.id))
          : unknown(
              item,
              "Thiết bị chưa gán phòng; API trạng thái yêu cầu ID phòng.",
            ),
      );
      const incomplete = stock.filter((item) => item.available === null).length;
      failure = errors.length
        ? "Không tải được trạng thái một số phòng. " + errors.join("; ")
        : incomplete
          ? incomplete +
            " thiết bị chưa xác định được số lượng. Xem chi tiết để biết lý do."
          : "";
      message(
        failure ||
          (inventory.length
            ? "Đã tải dữ liệu API tại " +
              selected.startTime +
              "; chưa xác minh toàn khoảng giờ."
            : "API chưa có dữ liệu thiết bị."),
        Boolean(failure),
      );
    } catch (error) {
      if (requestId !== sequence) return;
      failure = apiError(error);
      if (!catalogReceived) loaded = false;
      stock = inventory.map((item) => unknown(item, failure));
      message("Không thể tải dữ liệu API. " + failure, true);
    } finally {
      clearTimeout(timeout);
      if (requestId === sequence) {
        loading = false;
        render();
      }
    }
  }
  function scheduleRefresh() {
    controller?.abort();
    sequence++;
    clearTimeout(debounce);
    stock = inventory.map((item) =>
      unknown(item, "Cần kiểm tra lại thời gian."),
    );
    loading = false;
    message("");
    page = 1;
    render();
    debounce = setTimeout(refresh, 250);
  }
  for (const id of ["statusDate", "statusStart", "statusEnd"]) {
    for (const event of ["input", "change"])
      $(id).addEventListener(event, scheduleRefresh);
  }
  for (const id of [
    "typeFilter",
    "stateFilter",
    "locationFilter",
    "conditionFilter",
    "pageSize",
  ]) {
    $(id).addEventListener("change", () => {
      page = 1;
      render();
    });
  }
  for (const id of ["equipmentSearch", "globalSearch"])
    $(id).addEventListener("input", () => {
      page = 1;
      render();
    });
  $("statusFilters").addEventListener("submit", (event) => {
    event.preventDefault();
    refresh();
  });
  $("advancedToggle").onclick = () => {
    const open = $("advancedPanel").hidden;
    $("advancedPanel").hidden = !open;
    $("advancedToggle").setAttribute("aria-expanded", String(open));
  };
  $("resetFilters").onclick = () => {
    for (const id of [
      "equipmentSearch",
      "globalSearch",
      "typeFilter",
      "stateFilter",
      "locationFilter",
      "conditionFilter",
    ])
      $(id).value = "";
    page = 1;
    refresh();
  };
  $("closeDetail").onclick = () => {
    selectedId = null;
    detailDismissed = true;
    render();
  };
  $("notifications").onclick = () =>
    message("Trang US13 chưa kết nối API thông báo.");
  $("menu-toggle").onclick = () => {
    const open = document.body.classList.toggle("menu-open");
    $("menu-toggle").setAttribute("aria-expanded", String(open));
  };
  document.addEventListener("click", (event) => {
    if (
      document.body.classList.contains("menu-open") &&
      !event.target.closest(".sidebar, #menu-toggle")
    ) {
      document.body.classList.remove("menu-open");
      $("menu-toggle").setAttribute("aria-expanded", "false");
    }
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      document.body.classList.remove("menu-open");
      $("menu-toggle").setAttribute("aria-expanded", "false");
    }
  });
  $("statusDate").value = today();
  $("statusStart").value = "09:00";
  $("statusEnd").value = "11:00";
  window.US13 = Object.freeze({ refresh });
  window.addEventListener("us13-inventory-changed", refresh);
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) refresh();
  });
  window.addEventListener("focus", () => {
    if (!loading) refresh();
  });
  setInterval(() => {
    if (!document.hidden && !loading) refresh();
  }, 30000);
  refresh();
})();
