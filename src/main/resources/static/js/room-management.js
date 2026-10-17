"use strict";
(() => {
  const statuses = {
    ACTIVE: "Đang hoạt động",
    MAINTENANCE: "Bảo trì",
    INACTIVE: "Ngừng sử dụng",
  };
  const equipment = [
    { id: "screen", name: "Màn hình" },
    { id: "projector", name: "Máy chiếu" },
    { id: "micro", name: "Micro" },
    { id: "wifi", name: "Wifi" },
    { id: "camera", name: "Camera" },
  ];
  const initialRooms = [
    "A101",
    "A102",
    "B201",
    "B202",
    "C301",
    "C302",
    "D401",
    "D402",
  ].map((code, index) => ({
    id: String(index + 1),
    name: "Phòng " + code,
    building: "Tòa " + code[0],
    floor: Number(code[1]),
    capacity: [10, 20, 15, 30, 12, 25, 40, 50][index],
    status:
      index === 3
        ? "MAINTENANCE"
        : [5, 7].includes(index)
          ? "INACTIVE"
          : "ACTIVE",
    location: "Gần thang máy, khu " + code[0],
    description: "",
    devices: ["screen", "projector", "micro", "wifi"],
    image: index % 6,
  }));
  // Repository mô phỏng: thay lớp này bằng adapter API khi có Backend.
  let rooms = structuredClone(initialRooms);
  const bookings = [
    {
      roomId: "1",
      title: "Họp phòng ban",
      participantCount: 8,
      start: Date.now() + 86400000,
    },
    {
      roomId: "2",
      title: "Họp dự án",
      participantCount: 18,
      start: Date.now() + 172800000,
    },
    {
      roomId: "3",
      title: "Họp kế hoạch",
      participantCount: 12,
      start: Date.now() + 86400000,
    },
  ];
  const fold = (value) =>
    String(value)
      .trim()
      .toLocaleLowerCase("vi")
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/đ/g, "d");
  function validate(value, existing, allRooms, futureBookings) {
    if (!value.name.trim()) return "Vui lòng nhập tên phòng.";
    if (value.name.length > 80) return "Tên phòng tối đa 80 ký tự.";
    if (
      allRooms.some(
        (r) => r.id !== existing?.id && fold(r.name) === fold(value.name),
      )
    )
      return "Tên phòng đã tồn tại. Vui lòng chọn tên khác.";
    if (!["Tòa A", "Tòa B", "Tòa C", "Tòa D"].includes(value.building))
      return "Vui lòng chọn tòa nhà / khu vực hợp lệ.";
    if (!Number.isInteger(value.floor) || value.floor < -5 || value.floor > 100)
      return "Tầng phải là số nguyên từ -5 đến 100.";
    if (
      !Number.isInteger(value.capacity) ||
      value.capacity < 1 ||
      value.capacity > 1000
    )
      return "Sức chứa phải là số nguyên từ 1 đến 1000.";
    if (!Object.hasOwn(statuses, value.status))
      return "Vui lòng chọn trạng thái hợp lệ.";
    if (value.location.length > 250 || value.description.length > 1000)
      return "Vị trí hoặc mô tả vượt quá độ dài cho phép.";
    if (value.devices.some((id) => !equipment.some((item) => item.id === id)))
      return "Thiết bị không hợp lệ.";
    if (!Number.isInteger(value.image) || value.image < 0 || value.image > 5)
      return "Ảnh minh họa không hợp lệ.";
    const conflict = futureBookings.find(
      (b) =>
        b.roomId === existing?.id &&
        b.start > Date.now() &&
        b.participantCount > value.capacity,
    );
    if (conflict)
      return (
        "Không thể giảm sức chứa xuống " +
        value.capacity +
        " người: lịch “" +
        conflict.title +
        "” sắp tới có " +
        conflict.participantCount +
        " người. Các lịch hiện có được giữ nguyên."
      );
    return "";
  }
  const repository = {
    list: () => structuredClone(rooms),
    save: (value, id) => {
      const index = rooms.findIndex((r) => r.id === id);
      const saved = {
        ...structuredClone(value),
        id: id || String(Math.max(0, ...rooms.map((r) => Number(r.id))) + 1),
      };
      if (index >= 0) rooms[index] = saved;
      else rooms.push(saved);
      return structuredClone(saved);
    },
  };
  globalThis.US11Rooms = {
    validate,
    initialRooms: structuredClone(initialRooms),
  };
  if (typeof document === "undefined") return;
  const $ = (id) => document.getElementById(id);
  const paths = {
    calendar: "M4 5h16v16H4z M8 3v4 M16 3v4 M4 10h16 M8 14h2 M14 14h2 M8 18h2",
    room: "M4 21V4l9-2v19 M13 5h7v16 M8 12v3 M16 9h1 M16 13h1 M16 17h1",
    check: "m5 12 4 4L19 6",
    ban: "M5 5l14 14 M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0",
    tool: "m14 6 4 4 4-4a7 7 0 0 1-9 9l-7 7-4-4 7-7a7 7 0 0 1 9-9z",
    users:
      "M16 21v-3a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v3 M13 6a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M17 3a4 4 0 0 1 0 8 M22 21v-3a4 4 0 0 0-3-4",
    clock: "M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0 M12 6v6l4 2",
    chart: "M4 3v18h18 M8 16v-5 M13 16V6 M18 16v-9",
    menu: "M3 5h18 M3 12h18 M3 19h18",
    bell: "M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9 M10 21h4",
    search: "M16 16l5 5 M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0",
    plus: "M12 4v16 M4 12h16",
    info: "M12 10v7 M12 6v1 M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0",
    edit: "m15 3 6 6-12 12H3v-6z M12 6l6 6",
    screen: "M2 3h20v14H2z M12 17v4 M7 21h10",
    projector: "M3 8h18v12H3z M8 8V5h8v3 M8 12v4 M16 12v4",
    micro: "M9 3h6v12H9z M6 11v3a6 6 0 0 0 12 0v-3 M12 20v3",
    wifi: "M2 8a16 16 0 0 1 20 0 M5 12a11 11 0 0 1 14 0 M8 16a6 6 0 0 1 8 0 M12 20h.01",
    camera: "M3 6h12v14H3z M15 11l6-3v10l-6-3",
  };
  function icon(type) {
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("viewBox", "0 0 24 24");
    svg.setAttribute("aria-hidden", "true");
    const p = document.createElementNS(svg.namespaceURI, "path");
    p.setAttribute("d", paths[type] || paths.room);
    svg.append(p);
    return svg;
  }
  function node(tag, text, cls) {
    const n = document.createElement(tag);
    if (text != null) n.textContent = text;
    if (cls) n.className = cls;
    return n;
  }
  document
    .querySelectorAll("[data-icon]")
    .forEach((el) => el.append(icon(el.dataset.icon)));
  const rawRole = new URLSearchParams(location.search).get("role") || "ADMIN",
    role = rawRole.toUpperCase(),
    isAdmin = role === "ADMIN";
  $("role-label").textContent = isAdmin ? "Admin" : role;
  $("permission-warning").hidden = isAdmin;
  let editingId = null,
    page = 1;
  function message(text) {
    $("admin-message").textContent = text;
  }
  function badge(status) {
    const e = node("span", null, "status-badge status-" + status);
    e.append(
      icon(
        status === "ACTIVE"
          ? "check"
          : status === "MAINTENANCE"
            ? "tool"
            : "ban",
      ),
      document.createTextNode(statuses[status]),
    );
    return e;
  }
  function setTab(id) {
    for (const name of ["basic", "equipment"]) {
      const selected = id === name;
      $(name + "-panel").hidden = !selected;
      $(name + "-tab").classList.toggle("active", selected);
      $(name + "-tab").setAttribute("aria-selected", String(selected));
    }
  }
  function impact() {
    const future = bookings.filter(
      (b) => b.roomId === editingId && b.start > Date.now(),
    );
    $("booking-impact").hidden = !future.length;
    if (future.length)
      $("booking-impact").textContent =
        "Phòng có " +
        future.length +
        " lịch đặt mẫu sắp tới (" +
        future
          .map((b) => b.title + ": " + b.participantCount + " người")
          .join("; ") +
        "). Đổi sức chứa hoặc trạng thái chỉ áp dụng cho yêu cầu đặt mới; không tự sửa hay xóa các lịch này.";
  }
  function openEditor(room = null) {
    if (!isAdmin) return;
    clearFieldErrors();
    editingId = room?.id || null;
    $("room-form").reset();
    $("editor-title").textContent = room ? "Sửa phòng họp" : "Thêm phòng họp";
    $("room-name").value = room?.name || "";
    $("room-building").value = room?.building || "";
    $("room-floor").value = room?.floor ?? "";
    $("room-capacity").value = room?.capacity ?? "";
    $("room-location").value = room?.location || "";
    $("room-description").value = room?.description || "";
    $("room-image").value = String(room?.image ?? 0);
    document.querySelector(
      'input[name="room-status"][value="' + (room?.status || "ACTIVE") + '"]',
    ).checked = true;
    document
      .querySelectorAll('[name="device"]')
      .forEach((c) => (c.checked = (room?.devices || []).includes(c.value)));
    $("form-error").hidden = true;
    $("editor").hidden = false;
    document.querySelector(".admin-layout").classList.remove("editor-closed");
    setTab("basic");
    impact();
    render();
  }
  function closeEditor() {
    $("editor").hidden = true;
    editingId = null;
    document.querySelector(".admin-layout").classList.add("editor-closed");
    render();
  }
  function quickStatus(room, status) {
    if (!isAdmin) return;
    openEditor(room);
    document.querySelector(
      'input[name="room-status"][value="' + status + '"]',
    ).checked = true;
    message(
      "Đã điền trạng thái " +
        statuses[status] +
        ". Kiểm tra biểu mẫu rồi nhấn Lưu để xác nhận thay đổi mô phỏng.",
    );
  }
  function render() {
    const all = repository.list();
    $("total").textContent = all.length;
    for (const [s, id] of [
      ["ACTIVE", "active"],
      ["MAINTENANCE", "maintenance"],
      ["INACTIVE", "inactive"],
    ]) {
      const n = all.filter((r) => r.status === s).length;
      $(id).textContent = n;
      $(id + "-percent").textContent =
        (all.length ? (n / all.length) * 100 : 0).toFixed(1) + "%";
    }
    const filtered = all.filter(
      (r) =>
        (!$("building-filter").value ||
          r.building === $("building-filter").value) &&
        (!$("status-filter").value || r.status === $("status-filter").value) &&
        r.capacity >= Number($("capacity-filter").value) &&
        fold(r.name + " " + r.building + " " + r.location).includes(
          fold($("search").value),
        ),
    );
    const size = Number($("page-size").value),
      pages = Math.max(1, Math.ceil(filtered.length / size));
    page = Math.min(page, pages);
    const start = (page - 1) * size;
    $("admin-rooms").replaceChildren();
    filtered.slice(start, start + size).forEach((r, index) => {
      const tr = node("tr");
      tr.classList.toggle("editing", r.id === editingId);
      tr.append(node("td", start + index + 1));
      const pic = node("td"),
        photo = node("div", null, "admin-photo");
      photo.setAttribute("role", "img");
      photo.setAttribute("aria-label", "Ảnh minh họa " + r.name);
      photo.style.backgroundPosition =
        [0, 50, 100][r.image % 3] + "% " + (r.image < 3 ? 0 : 100) + "%";
      pic.append(photo);
      tr.append(
        pic,
        node("td", r.name),
        node("td", r.building),
        node("td", r.location || "—"),
        node("td", r.floor),
        node("td", r.capacity),
      );
      const devices = node("td"),
        icons = node("div", null, "admin-devices");
      icons.title =
        r.devices
          .map((id) => equipment.find((e) => e.id === id).name)
          .join(", ") || "Không có thiết bị";
      icons.setAttribute("aria-label", icons.title);
      r.devices.forEach((id) => icons.append(icon(id)));
      devices.append(icons);
      const state = node("td");
      state.append(badge(r.status));
      tr.append(devices, state);
      const actions = node("td"),
        wrap = node("div", null, "row-actions"),
        edit = node("button");
      edit.type = "button";
      edit.disabled = !isAdmin;
      edit.append(icon("edit"), document.createTextNode("Sửa"));
      edit.setAttribute("aria-label", "Sửa " + r.name);
      edit.onclick = () => openEditor(r);
      if (isAdmin) wrap.append(edit);
      if (isAdmin) {
        const details = node("details"),
          summary = node("summary", "•••");
        summary.setAttribute("aria-label", "Đổi trạng thái " + r.name);
        const menu = node("div", null, "quick-menu");
        Object.entries(statuses).forEach(([s, title]) => {
          const b = node("button", title);
          b.type = "button";
          b.disabled = s === r.status;
          b.onclick = () => quickStatus(r, s);
          menu.append(b);
        });
        details.append(summary, menu);
        wrap.append(details);
      }
      actions.append(wrap);
      tr.append(actions);
      $("admin-rooms").append(tr);
    });
    $("result-count").textContent = filtered.length
      ? "Hiển thị " +
        (start + 1) +
        " - " +
        Math.min(start + size, filtered.length) +
        " của " +
        filtered.length +
        " phòng"
      : "0 phòng";
    $("empty-rooms").hidden = filtered.length > 0;
    $("page-number").textContent = page;
    $("previous").disabled = page <= 1;
    $("next").disabled = page >= pages;
    $("add-room").hidden = !isAdmin;
    $("add-room").disabled = !isAdmin;
    document
      .querySelectorAll(".room-list th:last-child, #admin-rooms td:last-child")
      .forEach((el) => (el.hidden = !isAdmin));
    document
      .querySelectorAll(
        "#room-form input,#room-form select,#room-form textarea,#save-room",
      )
      .forEach((el) => (el.disabled = !isAdmin));
  }
  equipment.forEach((item) => {
    const label = node("label"),
      check = node("input");
    check.type = "checkbox";
    check.name = "device";
    check.value = item.id;
    label.append(check, document.createTextNode(item.name));
    $("equipment-options").append(label);
  });
  $("basic-tab").onclick = () => setTab("basic");
  $("equipment-tab").onclick = () => setTab("equipment");
  $("add-room").onclick = () => {
    if (isAdmin) openEditor();
  };
  $("close-editor").onclick = closeEditor;
  $("cancel-edit").onclick = closeEditor;
  for (const id of [
    "building-filter",
    "status-filter",
    "capacity-filter",
    "page-size",
  ])
    $(id).addEventListener("change", () => {
      page = 1;
      render();
    });
  $("search").addEventListener("input", () => {
    page = 1;
    render();
  });
  $("previous").onclick = () => {
    page--;
    render();
  };
  $("next").onclick = () => {
    page++;
    render();
  };

  const errorFields = [
    "room-name",
    "room-building",
    "room-floor",
    "room-capacity",
    "room-location",
  ];
  function clearFieldErrors() {
    errorFields.forEach((id) => {
      const field = $(id);
      field.removeAttribute("aria-invalid");
      const error = $(id + "-error");
      if (error) {
        error.textContent = "";
        error.hidden = true;
      }
    });
  }
  function validateFields() {
    const values = {
      name: $("room-name").value.trim(),
      floor: $("room-floor").value,
      capacity: $("room-capacity").value,
    };
    const errors = {};
    if (!values.name) errors["room-name"] = "Vui lòng nhập tên phòng.";
    else if (
      repository
        .list()
        .some((r) => r.id !== editingId && fold(r.name) === fold(values.name))
    )
      errors["room-name"] = "Tên phòng đã tồn tại.";
    if (!$("room-building").value)
      errors["room-building"] = "Vui lòng chọn tòa nhà / khu vực.";
    if (
      values.floor === "" ||
      !Number.isInteger(Number(values.floor)) ||
      Number(values.floor) < -5 ||
      Number(values.floor) > 100
    )
      errors["room-floor"] = "Nhập tầng là số nguyên từ -5 đến 100.";
    if (
      values.capacity === "" ||
      !Number.isInteger(Number(values.capacity)) ||
      Number(values.capacity) < 1 ||
      Number(values.capacity) > 1000
    )
      errors["room-capacity"] = "Sức chứa phải là số nguyên từ 1 đến 1000.";
    clearFieldErrors();
    for (const [id, text] of Object.entries(errors)) {
      $(id).setAttribute("aria-invalid", "true");
      $(id + "-error").textContent = text;
      $(id + "-error").hidden = false;
    }
    return Object.keys(errors);
  }
  errorFields.forEach((id) => {
    const field = $(id),
      error = node("span", null, "field-error");
    error.id = id + "-error";
    error.hidden = true;
    field.setAttribute("aria-describedby", error.id);
    field.after(error);
    field.addEventListener("blur", () => {
      validateFields();
    });
    field.addEventListener("input", () => {
      if (field.getAttribute("aria-invalid") === "true") validateFields();
      $("form-error").hidden = true;
    });
    field.addEventListener("change", () => {
      validateFields();
      $("form-error").hidden = true;
    });
  });

  $("room-form").addEventListener("submit", (event) => {
    event.preventDefault();
    if (!isAdmin) {
      message("Chỉ Admin được thay đổi dữ liệu phòng.");
      return;
    }
    const old = repository.list().find((r) => r.id === editingId);
    const value = {
      name: $("room-name").value.trim(),
      building: $("room-building").value,
      floor: $("room-floor").value === "" ? NaN : Number($("room-floor").value),
      capacity:
        $("room-capacity").value === ""
          ? NaN
          : Number($("room-capacity").value),
      location: $("room-location").value.trim(),
      description: $("room-description").value.trim(),
      status: document.querySelector('[name="room-status"]:checked')?.value,
      devices: [...document.querySelectorAll('[name="device"]:checked')].map(
        (c) => c.value,
      ),
      image: Number($("room-image").value),
    };
    const invalidFields = validateFields();
    const error = validate(value, old, repository.list(), []);
    $("form-error").hidden = !error;
    $("form-error").textContent = error;
    if (error) {
      setTab("basic");
      if (invalidFields.length) $(invalidFields[0]).focus();
      else $("form-error").scrollIntoView({ block: "nearest" });
      return;
    }
    if (
      old &&
      (old.status !== value.status || old.capacity !== value.capacity) &&
      bookings.some((b) => b.roomId === old.id && b.start > Date.now())
    ) {
      if (
        !window.confirm(
          "Phòng có lịch đặt mẫu sắp tới. Thay đổi sức chứa hoặc trạng thái có thể ảnh hưởng việc sử dụng phòng. Phòng bảo trì/ngừng sử dụng sẽ bị khóa cho đặt mới. Các lịch đã đặt không bị sửa hoặc xóa. Tiếp tục kiểm tra và lưu thay đổi mô phỏng?",
        )
      )
        return;
    }
    const bookingError = validate(value, old, repository.list(), bookings);
    if (bookingError) {
      $("form-error").hidden = false;
      $("form-error").textContent = bookingError;
      setTab("basic");
      $("room-capacity").setAttribute("aria-invalid", "true");
      $("room-capacity-error").textContent = bookingError;
      $("room-capacity-error").hidden = false;
      $("room-capacity").focus();
      return;
    }
    const saved = repository.save(value, editingId);
    window.dispatchEvent(
      new CustomEvent("us11-rooms-changed", {
        detail: { rooms: repository.list(), simulated: true },
      }),
    );
    page = 1;
    openEditor(saved);
    message(
      (old ? "Đã cập nhật" : "Đã thêm") +
        " “" +
        saved.name +
        "” trong dữ liệu mô phỏng. Không lưu database.",
    );
  });
  $("menu-toggle").onclick = () => {
    const open = document.body.classList.toggle("menu-open");
    $("menu-toggle").setAttribute("aria-expanded", String(open));
  };
  // Chỉ dữ liệu mô phỏng trong bộ nhớ; không thay đổi lịch đặt hay dữ liệu của US khác.
  window.US11MockRooms = {
    getRooms: repository.list,
    getBookings: () => structuredClone(bookings),
  };
  if (isAdmin) {
    openEditor();
    closeEditor();
  } else closeEditor();
})();
