"use strict";
(() => {
  const statuses = {
    ACTIVE: "Đang hoạt động",
    MAINTENANCE: "Bảo trì",
    CONSTRUCTION: "Đang thi công",
    INACTIVE: "Ngừng sử dụng",
  };
  const equipment = [
    { id: "screen", name: "Màn hình" },
    { id: "projector", name: "Máy chiếu" },
    { id: "micro", name: "Micro" },
    { id: "wifi", name: "Wifi" },
    { id: "camera", name: "Camera" },
  ];
  let rooms = [],
    loading = false,
    saving = false;
  const fold = (value) =>
    String(value)
      .trim()
      .toLocaleLowerCase("vi")
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/đ/g, "d");
  function validate(value, existing, allRooms) {
    if (!value.name.trim()) return "Vui lòng nhập tên phòng.";
    if (value.name.length > 80) return "Tên phòng tối đa 80 ký tự.";
    if (
      allRooms.some(
        (r) => r.id !== existing?.id && fold(r.name) === fold(value.name),
      )
    )
      return "Tên phòng đã tồn tại. Vui lòng chọn tên khác.";
    if (!value.building.trim() || value.building.length > 80)
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
    return "";
  }
  function normalizeRoom(item) {
    if (
      !item ||
      item.roomId == null ||
      !Number.isInteger(item.capacity) ||
      item.capacity < 1
    )
      throw Error("API trả thông tin phòng không hợp lệ.");
    const rawLocation = String(item.location || "");
    const building =
      rawLocation.match(/Tòa(?: nhà)?\s+[^–,;-]+/iu)?.[0]?.trim() || "";
    const floor = rawLocation.match(/Tầng\s+(-?\d+)/iu);
    const location = rawLocation
      .replace(/Tòa(?: nhà)?\s+[^–,;-]+/iu, "")
      .replace(/Tầng\s+-?\d+/iu, "")
      .replace(/^[\s–,;-]+|[\s–,;-]+$/g, "");
    const rawStatus = String(item.status || "INACTIVE").toUpperCase();
    return {
      id: String(item.roomId),
      name: String(item.roomName || item.roomId),
      capacity: item.capacity,
      building,
      floor: floor ? Number(floor[1]) : null,
      location,
      rawLocation,
      qrCode: item.qrCode ?? null,
      rawStatus,
      status:
        rawStatus === "AVAILABLE"
          ? "ACTIVE"
          : rawStatus === "UNAVAILABLE"
            ? "INACTIVE"
            : rawStatus,
      devices: [],
      description: "",
      image:
        [...String(item.roomId)].reduce((n, c) => n + c.codePointAt(0), 0) % 6,
    };
  }
  async function request(path, method = "GET", body) {
    const headers = { Accept: "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    const token = await window.MeetingAuth?.getAccessToken?.();
    if (token) headers.Authorization = "Bearer " + token;
    const csrf = document.querySelector('meta[name="_csrf"]');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
    if (csrf?.content)
      headers[csrfHeader?.content || "X-CSRF-TOKEN"] = csrf.content;
    else {
      const cookie = document.cookie
        .split("; ")
        .find((value) => value.startsWith("XSRF-TOKEN="));
      if (cookie)
        headers["X-XSRF-TOKEN"] = decodeURIComponent(cookie.slice(11));
    }
    let response;
    try {
      response = await fetch(path, {
        method,
        headers,
        credentials: "same-origin",
        signal: AbortSignal.timeout(10000),
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      });
    } catch (error) {
      throw Error(
        error.name === "TimeoutError"
          ? "API phòng phản hồi quá chậm. Với thao tác lưu, hãy tải lại danh sách để kiểm tra trước khi thử lại."
          : "Không kết nối được API phòng. Hãy kiểm tra Backend và kết nối mạng.",
      );
    }
    const text = await response.text();
    let data;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      throw Error("API phòng không trả JSON hợp lệ.");
    }
    if (!response.ok) {
      if (response.status === 401 || response.status === 403) {
        isAdmin = false;
        closeEditor();
      }
      const error = Error(
        response.status === 401
          ? "Vui lòng đăng nhập để truy cập API phòng."
          : response.status === 403
            ? "Không có quyền thực hiện thao tác này. Chỉ Admin được quản lý phòng."
            : data?.message ||
              data?.detail ||
              "API phòng trả lỗi HTTP " + response.status + ".",
      );
      error.fields = data?.errors || {};
      throw error;
    }
    return data;
  }
  const repository = {
    list: () => structuredClone(rooms),
    async load() {
      const data = await request("/api/rooms");
      if (!Array.isArray(data))
        throw Error("API danh sách phòng không hợp lệ.");
      rooms = data.map(normalizeRoom);
      const building = $("building-filter").value;
      $("building-filter").replaceChildren(
        new Option("Tất cả", ""),
        ...[...new Set(rooms.map((room) => room.building).filter(Boolean))].map(
          (value) => new Option(value, value),
        ),
      );
      if (
        [...$("building-filter").options].some(
          (option) => option.value === building,
        )
      )
        $("building-filter").value = building;
      return repository.list();
    },
    async save(value, id) {
      const old = rooms.find((room) => room.id === id);
      const sameLocation =
        old &&
        old.building === value.building &&
        old.floor === value.floor &&
        old.location === value.location;
      const body = {
        roomName: value.name,
        capacity: value.capacity,
        location: sameLocation
          ? old.rawLocation
          : [value.building, "Tầng " + value.floor, value.location]
              .filter(Boolean)
              .join(" - "),
        qrCode: old?.qrCode ?? null,
        status:
          old && old.status === value.status ? old.rawStatus : value.status,
      };
      if (!id) body.roomId = value.roomId;
      const saved = normalizeRoom(
        await request(
          id ? "/api/rooms/" + encodeURIComponent(id) : "/api/rooms",
          id ? "PUT" : "POST",
          body,
        ),
      );
      const index = rooms.findIndex((room) => room.id === saved.id);
      if (index >= 0) rooms[index] = saved;
      else rooms.push(saved);
      return saved;
    },
  };
  globalThis.US11Rooms = { validate, normalizeRoom };
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
  let isAdmin = false;
  function applyIdentity(user) {
    const roles = Array.isArray(user?.roles)
      ? user.roles
      : [user?.role ?? user?.roleName];
    isAdmin = roles.some(
      (role) =>
        String(
          typeof role === "object" ? (role?.roleName ?? role?.name) : role,
        ).toUpperCase() === "ADMIN",
    );
    $("role-label").textContent = isAdmin
      ? "Admin"
      : "Chưa xác nhận quyền Admin";
    $("permission-warning").hidden = isAdmin;
    if (!isAdmin) closeEditor();
    else render();
  }
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
    $("booking-impact").hidden = !editingId;
    $("booking-impact").textContent =
      "Thay đổi sức chứa hoặc trạng thái có thể ảnh hưởng các lịch đã đặt. Hệ thống kiểm tra sức chứa khi lưu; giao diện không sửa hay xóa lịch họp.";
  }
  function openEditor(room = null) {
    if (!isAdmin || saving || loading) return;
    clearFieldErrors();
    editingId = room?.id || null;
    $("room-form").reset();
    $("editor-title").textContent = room ? "Sửa phòng họp" : "Thêm phòng họp";
    $("room-code").value = room?.id || "";
    $("room-name").value = room?.name || "";
    if (
      room?.building &&
      ![...$("room-building").options].some(
        (option) => option.value === room.building,
      )
    )
      $("room-building").append(new Option(room.building, room.building));
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
  async function editRoom(id) {
    if (!isAdmin || saving || loading) return false;
    loading = true;
    render();
    message("Đang tải thông tin phòng mới nhất…");
    try {
      const room = normalizeRoom(
        await request("/api/rooms/" + encodeURIComponent(id)),
      );
      if (!isAdmin) return false;
      if (room.id !== id)
        throw Error("API trả mã phòng không khớp phòng đã chọn.");
      const index = rooms.findIndex((item) => item.id === id);
      if (index >= 0) rooms[index] = room;
      loading = false;
      openEditor(room);
      message("Đã tải thông tin phòng mới nhất từ API.");
      return true;
    } catch (error) {
      message(error.message);
      return false;
    } finally {
      loading = false;
      render();
    }
  }
  async function updateRoomStatus(room,next){
    if(!isAdmin||loading||saving)return;
    if(!confirm('Đổi '+room.name+' sang “'+statuses[next]+'”? Các lịch đã đặt không bị thay đổi. Phòng bảo trì/thi công sẽ không được đặt mới.')){render();return;}
    saving=true;render();try{await request('/api/rooms/'+encodeURIComponent(room.id)+'/status','POST',{status:next});await repository.load();message('Đã cập nhật '+room.name+': '+statuses[next]);}catch(error){message(error.message);}finally{saving=false;render();}
  }
  async function completeRoomMaintenance(room){
    if(!isAdmin||saving||loading)return;
    if(!window.confirm('Xác nhận '+room.name+' đã sửa xong và sẵn sàng sử dụng? Các lịch đã đặt vẫn được giữ nguyên.'))return;
    saving=true;render();
    try{await request('/api/rooms/'+encodeURIComponent(room.id)+'/restore','POST',{});await repository.load();message('Đã hoàn tất bảo trì '+room.name+'. Phòng đã sẵn sàng để đặt ở các giờ còn trống.');}
    catch(error){message(error.message);}finally{saving=false;render();}
  }
  async function quickStatus(room, status) {
    if (!isAdmin || saving || loading) return;
    if (!(await editRoom(room.id))) return;
    document.querySelector(
      'input[name="room-status"][value="' + status + '"]',
    ).checked = true;
    message(
      "Đã điền trạng thái " +
        statuses[status] +
        ". Kiểm tra biểu mẫu rồi nhấn Lưu để lưu thay đổi qua API.",
    );
  }
  function render() {
    $("role-label").textContent = isAdmin
      ? "Admin"
      : "Chưa xác nhận quyền Admin";
    $("permission-warning").hidden = isAdmin;
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
        node("td", r.floor ?? "—"),
        node("td", r.capacity),
      );
      const devices = node("td"),
        icons = node("div", null, "admin-devices");
      icons.title =
        r.devices
          .map((id) => equipment.find((e) => e.id === id).name)
          .join(", ") || "API phòng chưa cung cấp danh sách thiết bị";
      icons.setAttribute("aria-label", icons.title);
      r.devices.forEach((id) => icons.append(icon(id)));
      devices.append(icons);
      const state = node("td");
      state.append(badge(r.status));
      if(isAdmin){const change=node('select');change.dataset.roomStatus=r.id;change.setAttribute('aria-label','Cập nhật trạng thái '+r.name);for(const [value,title]of Object.entries(statuses))change.append(new Option(title,value));change.value=r.status;change.disabled=loading||saving;change.style.cssText='display:block;margin-top:8px;max-width:160px;padding:7px;border:1px solid #b9cde6;border-radius:6px';change.onchange=()=>updateRoomStatus(r,change.value);state.append(change);}
      tr.append(devices, state);
      const actions = node("td"),
        wrap = node("div", null, "row-actions"),
        edit = node("button");
      edit.type = "button";
      edit.disabled = !isAdmin || loading || saving;
      edit.append(icon("edit"), document.createTextNode("Sửa"));
      edit.setAttribute("aria-label", "Sửa " + r.name);
      edit.onclick = () => editRoom(r.id);
      if (isAdmin) wrap.append(edit);
      if(isAdmin&&['MAINTENANCE','CONSTRUCTION'].includes(r.status)){const restore=node('button','Hoàn tất bảo trì');restore.type='button';restore.dataset.restoreRoom=r.id;restore.disabled=loading||saving;restore.onclick=()=>completeRoomMaintenance(r);wrap.append(restore);}
      if (isAdmin) {
        const details = node("details"),
          summary = node("summary", "•••");
        summary.setAttribute("aria-label", "Đổi trạng thái " + r.name);
        const menu = node("div", null, "quick-menu");
        Object.entries(statuses).forEach(([s, title]) => {
          const b = node("button", title);
          b.type = "button";
          b.disabled = s === r.status || loading || saving;
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
    $("add-room").disabled = !isAdmin || loading || saving;
    $("close-editor").disabled = saving;
    $("cancel-edit").disabled = saving;
    $("reload-rooms").disabled = loading || saving;
    document
      .querySelectorAll(".room-list th:last-child, #admin-rooms td:last-child")
      .forEach((el) => (el.hidden = !isAdmin));
    document
      .querySelectorAll(
        "#room-form input,#room-form select,#room-form textarea,#save-room",
      )
      .forEach((el) => (el.disabled = !isAdmin || loading || saving));
    $("room-code").disabled = Boolean(editingId) || !isAdmin || saving;
    $("equipment-tab").hidden = true;
    $("room-description").disabled = true;
    $("room-image").disabled = true;
    document
      .querySelectorAll('[name="device"]')
      .forEach((el) => (el.disabled = true));
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
    "room-code",
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
      if (error || invalidFields.length) {
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
    if (
      !editingId &&
      (!$("room-code").value.trim() || $("room-code").value.trim().length > 50)
    )
      errors["room-code"] = "Nhập mã phòng từ 1 đến 50 ký tự.";
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

  $("room-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!isAdmin) {
      message("Chỉ Admin được thay đổi dữ liệu phòng.");
      return;
    }
    if (saving || loading) return;
    const old = repository.list().find((r) => r.id === editingId);
    const value = {
      roomId: $("room-code").value.trim(),
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
    const error = validate(value, old, repository.list());
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
      !window.confirm(
        "Thay đổi sức chứa hoặc trạng thái có thể ảnh hưởng lịch đã đặt. Phòng bảo trì/ngừng sử dụng không nhận lịch mới. Lịch cũ không bị tự sửa hoặc xóa. Tiếp tục lưu?",
      )
    )
      return;
    saving = true;
    render();
    try {
      const saved = await repository.save(value, editingId);
      saving = false;
      page = 1;
      openEditor(saved);
      window.dispatchEvent(
        new CustomEvent("us11-rooms-changed", {
          detail: { rooms: repository.list(), simulated: false },
        }),
      );
      message(
        (old ? "Đã cập nhật " : "Đã thêm ") + saved.name + " vào hệ thống.",
      );
    } catch (error) {
      $("form-error").hidden = false;
      $("form-error").textContent = error.message;
      const mapping = {
        roomId: "room-code",
        roomName: "room-name",
        capacity: "room-capacity",
        location: "room-location",
      };
      for (const [key, text] of Object.entries(error.fields || {})) {
        const id = mapping[key];
        if (!id) continue;
        $(id).setAttribute("aria-invalid", "true");
        $(id + "-error").textContent = text;
        $(id + "-error").hidden = false;
      }
      message(error.message);
    } finally {
      saving = false;
      render();
    }
  });
  $("menu-toggle").onclick = () => {
    const open = document.body.classList.toggle("menu-open");
    $("menu-toggle").setAttribute("aria-expanded", String(open));
  };
  async function reload() {
    if (loading || saving) return;
    loading = true;
    render();
    message("Đang tải danh sách phòng…");
    try {
      await repository.load();
      message("Đã tải danh sách phòng từ API.");
    } catch (error) {
      rooms = [];
      message(error.message);
    } finally {
      loading = false;
      render();
    }
  }
  window.US11Session = { setUser: applyIdentity };
  window.addEventListener("meeting-auth-changed", (event) =>
    applyIdentity(event.detail),
  );
  $("reload-rooms").onclick = reload;
  closeEditor();
  async function initialize() {
    try {
      const auth = window.MeetingAuth;
      const user =
        typeof auth?.getCurrentUser === "function"
          ? await auth.getCurrentUser()
          : auth?.user;
      applyIdentity(user || null);
    } catch {
      applyIdentity(null);
      message(
        "Chưa xác nhận được phiên đăng nhập. Thao tác quản lý chỉ dành cho Admin.",
      );
    }
    await reload();
  }
  window.addEventListener('ttcs-data-updated',event=>{event.preventDefault();const refresh=()=>{if(saving||loading){setTimeout(refresh,700);return;}repository.load().then(render).catch(error=>message(error.message));};refresh();});
  initialize();
})();
