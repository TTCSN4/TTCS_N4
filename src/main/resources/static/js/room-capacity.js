"use strict";
(() => {
  const samples = [
    {
      id: "1",
      name: "Phòng A101",
      building: "Tòa A",
      location: "Tòa A - Tầng 1",
      capacity: 10,
      status: "AVAILABLE",
    },
    {
      id: "2",
      name: "Phòng A102",
      building: "Tòa A",
      location: "Tòa A - Tầng 1",
      capacity: 20,
      status: "AVAILABLE",
    },
    {
      id: "3",
      name: "Phòng B201",
      building: "Tòa B",
      location: "Tòa B - Tầng 2",
      capacity: 15,
      status: "AVAILABLE",
    },
    {
      id: "4",
      name: "Phòng B202",
      building: "Tòa B",
      location: "Tòa B - Tầng 2",
      capacity: 30,
      status: "AVAILABLE",
    },
    {
      id: "5",
      name: "Phòng C301",
      building: "Tòa C",
      location: "Tòa C - Tầng 3",
      capacity: 12,
      status: "MAINTENANCE",
    },
    {
      id: "6",
      name: "Phòng C302",
      building: "Tòa C",
      location: "Tòa C - Tầng 3",
      capacity: 25,
      status: "AVAILABLE",
    },
    {
      id: "7",
      name: "Phòng D401",
      building: "Tòa D",
      location: "Tòa D - Tầng 4",
      capacity: 40,
      status: "AVAILABLE",
    },
    {
      id: "8",
      name: "Phòng D402",
      building: "Tòa D",
      location: "Tòa D - Tầng 4",
      capacity: 50,
      status: "AVAILABLE",
    },
  ].map((room) => ({
    ...room,
    devices:
      room.id === "8"
        ? ["Màn hình", "Micro"]
        : ["Máy chiếu", "Màn hình", "Camera", "Micro"],
  }));
  const PARTICIPANTS_KEY = "meeting.us01.participants.v1";
  function normalizeParticipants(value) {
    if (
      !value ||
      !Array.isArray(value.participantIds) ||
      !value.participantIds.length
    )
      throw Error("Chưa nhận danh sách ID người tham dự từ US01.");
    const valid = (id) =>
      (typeof id === "string" && id.trim()) ||
      (typeof id === "number" && Number.isSafeInteger(id) && id >= 0);
    if (value.participantIds.some((id) => !valid(id)))
      throw Error("Danh sách ID không hợp lệ.");
    const ids = [
      ...new Set(value.participantIds.map((id) => String(id).trim())),
    ];
    if (value.organizerId != null && !valid(value.organizerId))
      throw Error("ID chủ trì không hợp lệ.");
    if (
      value.organizerAttends != null &&
      typeof value.organizerAttends !== "boolean"
    )
      throw Error("Trạng thái tham dự của chủ trì không hợp lệ.");
    const organizerId =
      value.organizerId == null ? null : String(value.organizerId).trim();
    const organizerAttends = value.organizerAttends ?? Boolean(organizerId);
    if (organizerAttends && !organizerId)
      throw Error("Thiếu ID chủ trì tham dự.");
    const all = new Set(ids);
    if (organizerAttends) all.add(organizerId);
    if (all.size > 200)
      throw Error("Danh sách vượt giới hạn 200 người của dữ liệu mẫu.");
    return {
      participantIds: ids,
      organizerId,
      organizerAttends,
      count: all.size,
    };
  }
  function range(date, slot) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return null;
    const day = Date.parse(date + "T00:00:00Z");
    if (
      !Number.isFinite(day) ||
      new Date(day).toISOString().slice(0, 10) !== date
    )
      return null;
    const times = slot.split(",");
    if (
      times.length !== 2 ||
      times.some((t) => !/^([01]\d|2[0-3]):[0-5]\d$/.test(t))
    )
      return null;
    const start = Date.parse(date + "T" + times[0] + ":00+07:00"),
      end = Date.parse(date + "T" + times[1] + ":00+07:00");
    return end > start
      ? { date, start, end, startTime: times[0], endTime: times[1] }
      : null;
  }
  function demoBookings(date) {
    return [
      {
        roomId: "1",
        start: Date.parse(date + "T08:00:00+07:00"),
        end: Date.parse(date + "T09:00:00+07:00"),
      },
      {
        roomId: "2",
        start: Date.parse(date + "T09:00:00+07:00"),
        end: Date.parse(date + "T10:30:00+07:00"),
      },
      {
        roomId: "6",
        start: Date.parse(date + "T08:00:00+07:00"),
        end: Date.parse(date + "T12:00:00+07:00"),
      },
    ];
  }
  function roomStatus(room, query, bookings) {
    if (room.status === "MAINTENANCE") return "MAINTENANCE";
    if (room.status !== "AVAILABLE") return "INACTIVE";
    if (!query) return "UNKNOWN";
    return bookings.some(
      (b) =>
        b.status !== "CANCELLED" &&
        b.roomId === room.id &&
        b.start < query.end &&
        b.end > query.start,
    )
      ? "BUSY"
      : "AVAILABLE";
  }
  function choiceReason(room, query, context, role, bookings) {
    if (!["ADMIN", "PM"].includes(role))
      return "Chỉ Admin và PM được chọn phòng.";
    if (!context) return "Chưa nhận người tham dự từ US01.";
    if (!query) return "Ngày hoặc khoảng thời gian chưa hợp lệ.";
    const status = roomStatus(room, query, bookings);
    if (status === "MAINTENANCE") return "Phòng đang bảo trì.";
    if (status === "INACTIVE") return "Phòng đang ngừng sử dụng.";
    if (status === "BUSY") return "Phòng đã được đặt trong khung giờ.";
    if (context.count > room.capacity)
      return (
        "Tổng " +
        context.count +
        " người vượt sức chứa " +
        room.capacity +
        " người."
      );
    return "";
  }
  globalThis.US10Capacity = {
    samples,
    normalizeParticipants,
    range,
    demoBookings,
    roomStatus,
    choiceReason,
  };
  if (typeof document === "undefined") return;
  const $ = (id) => document.getElementById(id),
    node = (tag, text, cls) => {
      const e = document.createElement(tag);
      if (text != null) e.textContent = text;
      if (cls) e.className = cls;
      return e;
    };
  const paths = {
    calendar:
      '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 3v5m10-5v5M3 11h18M7 15h2m3 0h2M7 18h2m3 0h2"/>',
    rooms:
      '<path d="M4 21V3h16v18M3 21h18M8 7h1m6 0h1m-8 4h1m6 0h1m-8 4h1m6 0h1M10 21v-3h4v3"/>',
    users:
      '<circle cx="9" cy="7" r="3"/><path d="M2 21v-3a7 7 0 0 1 14 0v3M17 4a3 3 0 0 1 0 6m2 4a6 6 0 0 1 3 6"/>',
    clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l4 2"/>',
    chart: '<path d="M3 3v18h18M7 17v-5m5 5V7m5 10V4"/>',
    alert: '<path d="m12 3 10 18H2zM12 9v5m0 3h.01"/>',
    tools:
      '<path d="m4 3 5 5-2 3-5-4a7 7 0 0 0 9 8l7 7 4-4-7-7a7 7 0 0 0-8-9l4 4-3 3-5-5"/>',
    bell: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/>',
    refresh:
      '<path d="M20 9a8 8 0 0 0-14-4L3 8m0-5v5h5m-4 7a8 8 0 0 0 14 4l3-3m0 5v-5h-5"/>',
    menu: '<path d="M3 5h18M3 12h18M3 19h18"/>',
    search: '<circle cx="10" cy="10" r="7"/><path d="m15 15 6 6"/>',
    screen:
      '<rect x="3" y="4" width="18" height="13" rx="2"/><path d="M8 21h8m-4-4v4"/>',
    camera:
      '<rect x="2" y="5" width="13" height="14" rx="2"/><path d="m15 10 7-4v12l-7-4"/>',
    micro:
      '<rect x="9" y="2" width="6" height="13" rx="3"/><path d="M5 10v2a7 7 0 0 0 14 0v-2m-7 9v3m-3 0h6"/>',
    pin: '<path d="M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0Z"/><circle cx="12" cy="10" r="2"/>',
    check: '<path d="m5 12 4 4L19 6"/>',
    close: '<path d="m6 6 12 12M18 6 6 18"/>',
  };
  function icon(name) {
    const e = node("i");
    e.innerHTML =
      '<svg viewBox="0 0 24 24" aria-hidden="true">' +
      (paths[name] || paths.rooms) +
      "</svg>";
    return e;
  }
  document
    .querySelectorAll("[data-icon]")
    .forEach((el) => el.append(icon(el.dataset.icon)));
  const params = new URLSearchParams(location.search),
    role = (params.get("role") || "PM").toUpperCase();
  $("role-label").textContent = role;
  let context = null,
    usingDemo = false,
    selectedId = "3",
    currentQuery = null,
    bookings = [];
  function message(text, error = false) {
    $("page-status").textContent = text;
    $("page-status").classList.toggle("error", error);
  }
  function receive(value, demo = false) {
    try {
      context = normalizeParticipants(value);
      usingDemo = demo;
      message(
        demo
          ? "Đang dùng danh sách tài khoản mẫu để kiểm thử, chưa nhận dữ liệu thật từ US01."
          : "Đã nhận danh sách ID người tham dự từ US01.",
      );
    } catch (error) {
      context = null;
      usingDemo = false;
      message(error.message, true);
    }
    render();
  }
  const statusLabels = {
    AVAILABLE: "Còn trống",
    BUSY: "Đã đặt",
    MAINTENANCE: "Bảo trì",
    INACTIVE: "Ngừng sử dụng",
    UNKNOWN: "Chưa kiểm tra",
  };
  function badge(text, cls) {
    return node("span", text, "badge " + cls);
  }
  function photo(room, cls) {
    const e = node("div", null, cls),
      index = (Number(room.id) - 1) % 6;
    e.style.backgroundPosition =
      (index % 3) * 50 + "% " + Math.floor(index / 3) * 100 + "%";
    e.setAttribute("role", "img");
    e.setAttribute("aria-label", "Ảnh minh họa " + room.name);
    return e;
  }
  function stateBadge(room) {
    const s = roomStatus(room, currentQuery, bookings);
    return badge(
      statusLabels[s],
      s === "AVAILABLE"
        ? "good"
        : s === "BUSY"
          ? "busy"
          : s === "MAINTENANCE"
            ? "maintenance"
            : "neutral",
    );
  }
  function fitBadge(room) {
    return !context
      ? badge("Chưa có dữ liệu", "neutral")
      : badge(
          context.count <= room.capacity ? "✓ Phù hợp" : "× Không phù hợp",
          context.count <= room.capacity ? "good" : "bad",
        );
  }
  function renderCharts(rooms) {
    const total = rooms.length,
      busy = rooms.filter(
        (r) => roomStatus(r, currentQuery, bookings) === "BUSY",
      ).length,
      free = rooms.filter(
        (r) => roomStatus(r, currentQuery, bookings) === "AVAILABLE",
      ).length,
      maintenance = rooms.filter(
        (r) => roomStatus(r, currentQuery, bookings) === "MAINTENANCE",
      ).length;
    const pct = (n) => (total ? ((n / total) * 100).toFixed(1) : "0.0");
    for (const [key, value] of Object.entries({
      total,
      busy,
      free,
      maintenance,
    }))
      $("stat-" + key).textContent = value;
    $("stat-average").textContent = total
      ? (rooms.reduce((sum, r) => sum + r.capacity, 0) / total)
          .toFixed(1)
          .replace(".0", "")
      : "0";
    for (const key of ["busy", "free", "maintenance"])
      $("percent-" + key).textContent =
        pct({ busy, free, maintenance }[key]) + "%";
    const groups = [
        rooms.filter((r) => r.capacity <= 10).length,
        rooms.filter((r) => r.capacity > 10 && r.capacity <= 20).length,
        rooms.filter((r) => r.capacity > 20 && r.capacity <= 30).length,
        rooms.filter((r) => r.capacity > 30).length,
      ],
      labels = ["≤ 10 người", "11 - 20 người", "21 - 30 người", "> 30 người"],
      colors = ["#259df8", "#66ca90", "#ffa329", "#8963f3"];
    const max = Math.max(4, ...groups);
    let svg =
      '<svg viewBox="0 0 460 190" role="img" aria-label="Số phòng theo nhóm sức chứa">';
    for (let i = 0; i <= 4; i++) {
      const y = 150 - i * 30;
      svg +=
        '<line x1="39" y1="' +
        y +
        '" x2="452" y2="' +
        y +
        '" stroke="#eef3f8"/><text x="27" y="' +
        (y + 4) +
        '" fill="#526d92" font-size="11" text-anchor="end">' +
        Math.round((max * i) / 4) +
        "</text>";
    }
    groups.forEach((n, i) => {
      const x = 66 + i * 102,
        h = (n / max) * 120;
      svg +=
        '<rect x="' +
        x +
        '" y="' +
        (150 - h) +
        '" width="62" height="' +
        h +
        '" rx="4" fill="' +
        colors[i] +
        '"/><text x="' +
        (x + 31) +
        '" y="' +
        (143 - h) +
        '" text-anchor="middle" font-size="12" fill="#10233f">' +
        n +
        '</text><text x="' +
        (x + 31) +
        '" y="170" text-anchor="middle" font-size="11" fill="#375375">' +
        labels[i] +
        "</text>";
    });
    svg += "</svg>";
    $("capacity-chart").innerHTML = svg;
    $("usage-chart").style.background = total
      ? "conic-gradient(#069b41 0 " +
        pct(busy) +
        "%,#2199fa " +
        pct(busy) +
        "% " +
        ((busy + free) / total) * 100 +
        "%,#ff9c16 " +
        ((busy + free) / total) * 100 +
        "% 100%)"
      : "#edf2f7";
    $("usage-chart").setAttribute(
      "aria-label",
      busy +
        " phòng đang sử dụng, " +
        free +
        " phòng trống, " +
        maintenance +
        " phòng bảo trì",
    );
    $("donut-total").textContent = total;
    $("chart-legend").replaceChildren();
    [
      [busy, "Đang sử dụng", "#069b41"],
      [free, "Còn trống", "#2199fa"],
      [maintenance, "Bảo trì", "#ff9c16"],
    ].forEach(([n, label, color]) => {
      const row = node("div", null, "legend-item"),
        dot = node("span", null, "legend-dot");
      dot.style.background = color;
      row.append(dot, node("dt", label), node("dd", n + " (" + pct(n) + "%)"));
      $("chart-legend").append(row);
    });
  }
  function renderDetails() {
    const room = samples.find((r) => r.id === selectedId);
    $("room-detail").hidden = !room;
    $("detail-empty").hidden = Boolean(room);
    $("selected-brief").hidden = !room;
    $("attendee-count").textContent = context
      ? context.count + " người"
      : "Chưa có dữ liệu";
    $("attendee-source").textContent = context
      ? usingDemo
        ? "Danh sách tài khoản mẫu (không lưu database)."
        : "Nhận từ US01, đã loại ID trùng và xét chủ trì."
      : "Quay lại US01 để chọn người, hoặc thử dữ liệu mẫu bên dưới.";
    $("permission-note").hidden = ["ADMIN", "PM"].includes(role);
    $("permission-note").textContent = "Member không có quyền chọn phòng.";
    const fit = $("fit-state");
    fit.replaceChildren();
    fit.className = "fit-state";
    const why = room
      ? choiceReason(room, currentQuery, context, role, bookings)
      : "Chọn phòng trong danh sách.";
    if (!context) {
      fit.append(
        node("strong", "Chưa nhận danh sách người tham dự"),
        node(
          "p",
          "US10 không nhập số người thủ công. Hãy chọn người tại US01.",
        ),
      );
    } else if (why) {
      fit.classList.add("bad");
      fit.append(
        node(
          "strong",
          room && context.count > room.capacity
            ? "Không đủ sức chứa"
            : "Phòng chưa thể lựa chọn",
        ),
        node("p", why),
      );
    } else {
      fit.classList.add("good");
      fit.append(
        node("strong", "✓ Phù hợp với phòng được chọn"),
        node(
          "p",
          "Tổng " +
            context.count +
            " người không vượt sức chứa " +
            room.capacity +
            " người.",
        ),
      );
    }
    if (!room) return;
    $("selected-brief").replaceChildren(photo(room, "room-thumb"));
    const brief = node("div");
    brief.append(
      node("strong", room.name),
      node("p", "♙ Sức chứa: " + room.capacity + " người"),
      node("p", "⌖ " + room.location),
    );
    $("selected-brief").append(brief);
    const image = photo(room, "room-photo");
    image.id = "detail-photo";
    $("detail-photo").replaceWith(image);
    $("detail-info").replaceChildren();
    const detail = (label, value, type) => {
      const row = node("div"),
        dt = node("dt");
      dt.append(icon(type), document.createTextNode(label));
      const dd = node("dd");
      dd.append(
        typeof value === "string" ? document.createTextNode(value) : value,
      );
      row.append(dt, dd);
      $("detail-info").append(row);
    };
    detail("Tên phòng", room.name, "rooms");
    detail("Tòa nhà / Khu vực", room.location, "rooms");
    detail("Sức chứa tối đa", room.capacity + " người", "users");
    detail("Thiết bị", room.devices.join(", "), "screen");
    detail("Trạng thái hiện tại", stateBadge(room), "calendar");
    detail("Đánh giá phù hợp", fitBadge(room), "check");
    $("prepare-selection").disabled = Boolean(why);
  }
  function render() {
    currentQuery = range($("date").value, $("time-range").value);
    bookings = currentQuery ? demoBookings(currentQuery.date) : [];
    $("filter-error").hidden = Boolean(currentQuery);
    $("filter-error").textContent = currentQuery
      ? ""
      : "Vui lòng chọn ngày và khoảng thời gian hợp lệ.";
    const filtered = samples.filter(
      (r) =>
        (!$("building").value || r.building === $("building").value) &&
        r.capacity >= Number($("min-capacity").value) &&
        (!$("status").value ||
          roomStatus(r, currentQuery, bookings) === $("status").value),
    );
    renderCharts(filtered);
    const query = $("search")
      .value.toLocaleLowerCase("vi")
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/đ/g, "d");
    const rows = filtered.filter((r) =>
      (r.name + " " + r.location)
        .toLocaleLowerCase("vi")
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .replace(/đ/g, "d")
        .includes(query),
    );
    $("room-table").replaceChildren();
    rows.forEach((room, index) => {
      const tr = node("tr");
      tr.dataset.roomId = room.id;
      tr.classList.toggle("selected", room.id === selectedId);
      tr.append(node("td", index + 1));
      const name = node("td"),
        identity = node("div", null, "room-name");
      identity.append(photo(room, "room-thumb"), node("span", room.name));
      name.append(identity);
      tr.append(name, node("td", room.location));
      const capacity = node("td"),
        capacityInfo = node("span", null, "capacity-cell");
      capacityInfo.append(icon("users"), node("strong", room.capacity));
      capacity.append(capacityInfo);
      tr.append(capacity);
      const equipment = node("td"),
        list = node("span", null, "devices");
      list.title = room.devices.join(", ");
      for (const type of ["screen", "camera", "micro"]) list.append(icon(type));
      equipment.append(list);
      tr.append(equipment);
      const state = node("td"),
        fit = node("td");
      state.append(stateBadge(room));
      fit.append(fitBadge(room));
      tr.append(state, fit);
      const action = node("td"),
        button = node("button", null, "primary-button table-button"),
        why = choiceReason(room, currentQuery, context, role, bookings);
      button.type = "button";
      button.disabled = Boolean(why);
      button.title = why || "Chọn " + room.name;
      button.dataset.selectRoom = room.id;
      button.append(
        icon("calendar"),
        document.createTextNode(why ? "Không khả dụng" : "Chọn"),
      );
      button.addEventListener("click", (event) => {
        event.stopPropagation();
        selectedId = room.id;
        render();
      });
      action.append(button);
      tr.append(action);
      tr.addEventListener("click", () => {
        selectedId = room.id;
        render();
      });
      $("room-table").append(tr);
    });
    $("empty-state").hidden = rows.length > 0;
    renderDetails();
  }
  $("date").value =
    params.get("date") ||
    new Date(Date.now() + 7 * 3600000).toISOString().slice(0, 10);
  for (const id of ["date", "time-range", "building", "min-capacity", "status"])
    $(id).addEventListener("change", render);
  $("search").addEventListener("input", render);
  $("filters").addEventListener("submit", (e) => {
    e.preventDefault();
    render();
  });
  $("reset-filters").addEventListener("click", () => {
    $("date").value = new Date(Date.now() + 7 * 3600000)
      .toISOString()
      .slice(0, 10);
    $("time-range").value = "08:00,17:00";
    $("building").value = "";
    $("min-capacity").value = "0";
    $("status").value = "";
    $("search").value = "";
    render();
  });
  $("demo-attendees").addEventListener("click", () =>
    receive(
      {
        participantIds: Array.from(
          { length: 11 },
          (_, i) => "demo-user-" + (i + 1),
        ),
        organizerId: "demo-pm",
        organizerAttends: true,
      },
      true,
    ),
  );
  $("prepare-selection").addEventListener("click", () => {
    const room = samples.find((r) => r.id === selectedId);
    if (!room) return;
    const why = choiceReason(room, currentQuery, context, role, bookings);
    if (why) {
      message(why, true);
      return;
    }
    const selection = {
      roomId: room.id,
      roomName: room.name,
      capacity: room.capacity,
      date: currentQuery.date,
      startTime: currentQuery.startTime,
      endTime: currentQuery.endTime,
      participantIds: [...context.participantIds],
      organizerId: context.organizerId,
      organizerAttends: context.organizerAttends,
      participantCount: context.count,
      simulated: true,
    };
    try {
      sessionStorage.setItem(
        "meeting.us10.selection.v1",
        JSON.stringify(selection),
      );
      message(
        "Đã chuẩn bị thông tin phòng " +
          room.name +
          " và ID người tham dự trong phiên. Chưa tạo cuộc họp hoặc cập nhật database.",
      );
    } catch {
      message("Không thể lưu thông tin lựa chọn vào bộ nhớ phiên.", true);
    }
  });
  $("menu-toggle").addEventListener("click", () => {
    const open = document.body.classList.toggle("menu-open");
    $("menu-toggle").setAttribute("aria-expanded", String(open));
  });
  window.US10Participants = {
    setSelection: (value) => receive(value),
    getSelection: () => (context ? JSON.parse(JSON.stringify(context)) : null),
  };
  window.addEventListener("us01-participants-changed", (event) =>
    receive(event.detail),
  );
  try {
    const value = params.has("participantIds")
      ? {
          participantIds: JSON.parse(params.get("participantIds")),
          organizerId: params.get("organizerId"),
          organizerAttends: params.has("organizerAttends")
            ? JSON.parse(params.get("organizerAttends"))
            : undefined,
        }
      : JSON.parse(sessionStorage.getItem(PARTICIPANTS_KEY) || "null");
    if (value) receive(value);
    else render();
  } catch (error) {
    message(error.message, true);
    render();
  }
  if (params.get("demo") === "1") $("demo-attendees").click();
})();
