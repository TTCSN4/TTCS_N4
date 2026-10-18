(() => {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const shared = window.RoomAvailability;
  let participantContext = null,
    participantError = "";

  function preserveQuery() {
    document.querySelectorAll('a[href="rooms.html"]').forEach((link) => {
      link.href = location.pathname + location.search;
    });
  }
  function syncParticipantQuery(value) {
    const url = new URL(location.href);
    url.searchParams.set(
      "participantIds",
      JSON.stringify(value.participantIds),
    );
    if (value.organizerId !== null)
      url.searchParams.set("organizerId", value.organizerId);
    else url.searchParams.delete("organizerId");
    url.searchParams.set("organizerAttends", String(value.organizerAttends));
    history.replaceState(null, "", url.href);
    preserveQuery();
  }

  function loadParticipants() {
    try {
      const params = new URLSearchParams(location.search);
      let input;
      if (params.has("participantIds")) {
        input = {
          participantIds: JSON.parse(params.get("participantIds")),
          organizerId: params.get("organizerId"),
          organizerAttends: params.has("organizerAttends")
            ? JSON.parse(params.get("organizerAttends"))
            : undefined,
        };
      } else {
        const raw = sessionStorage.getItem(shared.PARTICIPANTS_KEY);
        input = raw ? JSON.parse(raw) : null;
      }
      participantContext = shared.participantSelection(input);
      if (params.has("participantIds")) {
        try {
          sessionStorage.setItem(
            shared.PARTICIPANTS_KEY,
            JSON.stringify(participantContext),
          );
        } catch {}
      }
      participantError = "";
    } catch (failure) {
      participantContext = null;
      participantError = failure.message;
    }
    renderParticipants();
  }
  function renderParticipants() {
    $("people").textContent = participantContext
      ? participantContext.participantCount + " người"
      : "Chưa nhận từ US01";
    $("participant-alert").hidden = Boolean(participantContext);
    $("participant-alert-message").textContent = participantContext
      ? ""
      : participantError ||
        "Chưa nhận danh sách người tham dự đã chọn từ US01.";
    $("participant-source").textContent = participantContext
      ? participantContext.participantIds.length +
        " ID đã chọn (đã loại trùng)" +
        (participantContext.organizerAttends
          ? "; tính cả chủ trì tham dự nếu chưa được chọn."
          : "; không cộng thêm chủ trì.")
      : "Quay lại US01, chọn tài khoản bằng checkbox rồi chuyển sang tìm phòng. Không nhập số người tại US07.";
    $("people").setAttribute("aria-invalid", String(!participantContext));
  }
  let rooms = [],
    snapshot = null,
    generation = 0,
    loading = false,
    selecting = false,
    selectedId = null,
    detailId = null,
    toastTimer,
    checkTimer;
  const normalize = (value) =>
    String(value || "")
      .toLocaleLowerCase("vi")
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/đ/g, "d");
  const deviceKey = (value) => {
    const key = normalize(value).replace(/[^a-z0-9]/g, "");
    return (
      {
        maychieu: "projector",
        wifi: "wifi",
        bangtrang: "board",
        bangviet: "board",
        tv: "tv",
      }[key] || key
    );
  };
  const node = (tag, text, className) => {
    const element = document.createElement(tag);
    if (text !== undefined) element.textContent = String(text);
    if (className) element.className = className;
    return element;
  };
  function icon(name) {
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("aria-hidden", "true");
    const use = document.createElementNS("http://www.w3.org/2000/svg", "use");
    use.setAttribute("href", "#i-" + name);
    svg.append(use);
    return svg;
  }
  const currentRange = () =>
    shared.range($("date").value, $("start-time").value, $("end-time").value);
  const currentSnapshot = () =>
    snapshot && snapshot.key === currentRange()?.key;
  const free = (room) => Boolean(currentSnapshot() && room.isAvailable);
  const people = () => {
    if (!participantContext)
      throw Error(
        participantError || "Chưa nhận danh sách người tham dự từ US01.",
      );
    return participantContext.participantCount;
  };
  const labelDate = (value) =>
    new Intl.DateTimeFormat("vi-VN", {
      timeZone: "Asia/Ho_Chi_Minh",
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
    }).format(new Date(value + "T12:00:00+07:00"));
  const rangeLabel = (range) =>
    `${labelDate(range.date)} · ${range.startTime} – ${range.endTime}`;
  function reason(room) {
    if (!currentSnapshot())
      return "Chọn khung giờ hợp lệ và đợi kiểm tra phòng.";
    if (!free(room))
      return (
        room.reason ||
        shared.roomReason(room) ||
        "Phòng không khả dụng trong khung giờ đã chọn."
      );
    let count;
    try {
      count = people();
    } catch {
      return participantError || "Chưa nhận danh sách người tham dự từ US01.";
    }
    if (room.capacity < count)
      return (
        "Không đủ sức chứa: cần " +
        count +
        ", phòng chứa " +
        room.capacity +
        " người."
      );
    if (currentRange().start <= Date.now())
      return "Khung giờ phải nằm trong tương lai.";
    return "";
  }
  function toast(message) {
    $("toast").textContent = message;
    $("toast").hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => ($("toast").hidden = true), 4200);
  }
  function error(message, field) {
    $("form-error").textContent = message;
    $("form-error").hidden = !message;
    for (const id of ["date", "start-time", "end-time"])
      $(id).setAttribute("aria-invalid", String(id === field));
  }
  function loadRooms() {
    const buildingOf = (location) => {
      const match = String(location || "").match(/Tòa(?: nhà)?\s+[^·,\-]+/i);
      return match ? match[0].trim() : String(location || "").trim();
    };
    rooms = shared.getRooms().map((room) => ({
      ...room,
      id: String(room.id),
      building: buildingOf(room.location),
    }));
    const previous = $("location").value;
    const previousBuilding = $("building").value;
    $("location").replaceChildren(new Option("Tất cả vị trí", ""));
    $("building").replaceChildren(new Option("Tất cả", ""));
    [...new Set(rooms.map((room) => room.location).filter(Boolean))]
      .sort((a, b) => a.localeCompare(b, "vi"))
      .forEach((location) =>
        $("location").append(new Option(location, location)),
      );
    if ([...$("location").options].some((option) => option.value === previous))
      $("location").value = previous;
    [...new Set(rooms.map((room) => room.building).filter(Boolean))]
      .sort((a, b) => a.localeCompare(b, "vi"))
      .forEach((building) =>
        $("building").append(new Option(building, building)),
      );
    if (
      [...$("building").options].some(
        (option) => option.value === previousBuilding,
      )
    )
      $("building").value = previousBuilding;
  }
  function devices(room) {
    const list = node("div", undefined, "device-list");
    if (!room.devices.length) {
      list.append(node("span", "Chưa cập nhật thiết bị", "device-tag"));
      return list;
    }
    for (const device of room.devices) {
      const tag = node("span", undefined, "device-tag"),
        key = deviceKey(device);
      if (key === "wifi" || key === "projector")
        tag.append(icon(key === "wifi" ? "wifi" : "projector"));
      tag.append(document.createTextNode(device));
      list.append(tag);
    }
    return list;
  }
  function badge(room) {
    const checked = currentSnapshot();
    return node(
      "span",
      (room.status === "MAINTENANCE"
        ? "Bảo trì"
        : !shared.roomActive(room)
          ? "Ngừng sử dụng"
          : "") ||
        (checked
          ? free(room)
            ? "Còn trống"
            : "Đã được đặt"
          : "Chưa kiểm tra"),
      `status-badge${checked ? (free(room) ? "" : " busy") : " unknown"}`,
    );
  }
  function button(text, className, iconName, listener) {
    const element = node("button", undefined, className);
    element.type = "button";
    element.append(node("span", text));
    if (iconName) element.append(icon(iconName));
    element.addEventListener("click", listener);
    return element;
  }
  function photo(room, className = "room-photo") {
    const el = node("div", undefined, className);
    const index =
      [...String(room.id)].reduce(
        (total, character) => total + character.codePointAt(0),
        0,
      ) % 6;
    el.style.backgroundPosition =
      (index % 3) * 50 + "% " + Math.floor(index / 3) * 100 + "%";
    el.setAttribute("role", "img");
    el.setAttribute("aria-label", "Ảnh minh họa " + room.name);
    return el;
  }
  function choosePreview(id) {
    selectedId = id;
    render();
  }
  function card(room) {
    const el = node("article", undefined, "room-card");
    el.dataset.roomId = room.id;
    el.classList.toggle("unavailable", Boolean(reason(room)));
    el.classList.toggle("is-selected", room.id === selectedId);
    const image = photo(room);
    image.append(badge(room));
    const body = node("div", undefined, "room-card-body");
    body.append(node("h3", room.name));
    const capacity = node("p", undefined, "card-meta");
    capacity.append(
      icon("users"),
      document.createTextNode("Sức chứa: " + room.capacity + " người"),
    );
    const location = node("p", undefined, "card-meta");
    location.append(icon("pin"), document.createTextNode(room.location));
    const equipment = node("p", undefined, "card-meta");
    equipment.append(
      icon("projector"),
      document.createTextNode(room.devices.join(", ")),
    );
    const explanation = node(
      "p",
      reason(room) || "Trống trong khoảng thời gian chọn",
      "room-unavailable-reason",
    );
    explanation.classList.toggle("is-free", !reason(room));
    const select = button(
      reason(room)
        ? "Không khả dụng"
        : room.id === selectedId
          ? "Đã chọn"
          : "Chọn phòng",
      "primary-button",
      undefined,
      () => choosePreview(room.id),
    );
    select.dataset.selectRoom = room.id;
    select.disabled = Boolean(reason(room)) || loading || selecting;
    const detail = button("Xem chi tiết", "details-button", undefined, () =>
      openDetail(room.id),
    );
    body.append(capacity, location, equipment, explanation, select, detail);
    el.append(image, body);
    return el;
  }
  function renderSelection() {
    if (!selectedId && currentSnapshot())
      selectedId = rooms.find((r) => !reason(r))?.id || null;
    const room = rooms.find((r) => r.id === selectedId);
    $("selection-placeholder").hidden = Boolean(room);
    $("selection-content").hidden = !room;
    $("continue-selection").disabled =
      !room || Boolean(room && reason(room)) || loading || selecting;
    if (!room) return;
    const image = photo(room);
    image.id = "selected-photo";
    image.append(node("span", "Đã chọn", "selected-photo-badge"));
    $("selected-photo").replaceWith(image);
    $("selected-name").textContent = room.name;
    $("selected-capacity").textContent =
      "♙  Sức chứa: " + room.capacity + " người";
    $("selected-location").textContent = "⌖  " + room.location;
    $("selected-devices").textContent = "▣  " + room.devices.join(", ");
    $("selected-state").textContent =
      reason(room) || "✓  Trống trong khoảng thời gian chọn";
    let count = 0;
    try {
      count = people();
    } catch {}
    $("selected-fit").textContent =
      reason(room) ||
      room.name + " đáp ứng " + count + "/" + room.capacity + " người tham dự";
    $("selected-timeline").replaceChildren();
    const range = currentRange();
    if (!range) return;
    const dayStart = Date.parse(range.date + "T08:00:00+07:00"),
      dayEnd = Date.parse(range.date + "T17:00:00+07:00");
    const clock = (value) =>
      new Intl.DateTimeFormat("en-GB", {
        timeZone: "Asia/Ho_Chi_Minh",
        hour: "2-digit",
        minute: "2-digit",
        hourCycle: "h23",
      }).format(new Date(value));
    function slot(start, end, busy) {
      if (end <= start) return;
      const row = node(
        "div",
        undefined,
        "timeline-slot " + (busy ? "busy" : "free"),
      );
      row.append(
        node("time", clock(start)),
        node("strong", busy ? "Đã được đặt" : "Khung giờ còn trống"),
        node("small", clock(start) + " – " + clock(end)),
      );
      $("selected-timeline").append(row);
    }
    if (!shared.roomActive(room)) {
      $("selected-timeline").append(node("p", shared.roomReason(room)));
      return;
    }
    try {
      const bookings = (snapshot.meetings || [])
        .filter(
          (b) =>
            String(b.roomId) === String(room.id) && b.status !== "CANCELLED",
        )
        .map((b) => ({
          start: Math.max(dayStart, Date.parse(b.startTime)),
          end: Math.min(dayEnd, Date.parse(b.endTime)),
        }))
        .filter((b) => b.end > b.start)
        .sort((a, b) => a.start - b.start);
      let cursor = dayStart;
      for (const booking of bookings) {
        slot(cursor, booking.start, false);
        slot(Math.max(cursor, booking.start), booking.end, true);
        cursor = Math.max(cursor, booking.end);
      }
      slot(cursor, dayEnd, false);
    } catch {
      $("selected-timeline").append(
        node("p", "Chưa đọc được lịch phòng từ hệ thống."),
      );
    }
  }
  function stats(count) {
    $("inline-stats").textContent =
      "Tổng số: " +
      rooms.length +
      " phòng  |  Còn trống: " +
      rooms.filter(free).length +
      "  |  Đang sử dụng: " +
      (currentSnapshot() ? snapshot.busyRoomsCount : 0) +
      "  |  Bảo trì: " +
      (currentSnapshot()
        ? snapshot.maintenanceRoomsCount
        : rooms.filter((r) => r.status === "MAINTENANCE").length);
    $("stat-total").textContent = rooms.length;
    $("stat-available").textContent = currentSnapshot()
      ? rooms.filter(free).length
      : "—";
    $("stat-busy").textContent = currentSnapshot()
      ? snapshot.busyRoomsCount
      : "—";
    $("stat-suitable").textContent =
      currentSnapshot() && count
        ? rooms.filter((room) => free(room) && room.capacity >= count).length
        : "—";
  }
  function render() {
    let count;
    try {
      count = people();
    } catch {
      count = null;
    }
    stats(count);
    $("room-list").setAttribute("aria-busy", String(loading));
    $("find-rooms").disabled = loading;
    $("find-rooms").querySelector("span").textContent = loading
      ? "Đang kiểm tra…"
      : "Tìm phòng trống";
    $("room-list").replaceChildren();
    if (loading) {
      for (let i = 0; i < 4; i++) {
        const placeholder = node("div", undefined, "loading-card");
        placeholder.setAttribute("aria-hidden", "true");
        $("room-list").append(placeholder);
      }
      renderSelection();
      $("empty-state").hidden = true;
      $("result-count").textContent = "…";
      $("query-summary").textContent = "Đang kiểm tra lịch phòng…";
      return;
    }
    const query = normalize($("search").value.trim()),
      location = $("location").value,
      state = $("availability-filter").value;
    const requested = [
      ...document.querySelectorAll('[name="device"]:checked'),
    ].map((input) => input.value);
    const filtered = rooms.filter((room) => {
      const fits =
        (!$("building").value || room.building === $("building").value) &&
        (!$("room-type").value ||
          ($("room-type").value === "large"
            ? room.capacity >= 20
            : room.capacity < 20)) &&
        (!location || room.location === location) &&
        normalize(room.name + " " + room.location).includes(query);
      const equipment = requested.every((key) =>
        (room.deviceKeys || room.devices.map(deviceKey)).includes(key),
      );
      const status =
        !currentSnapshot() ||
        state === "all" ||
        (state === "available"
          ? free(room)
          : !room.isAvailable &&
            room.status === "AVAILABLE" &&
            normalize(room.reason).includes("lich"));
      return fits && equipment && status;
    });
    const sort = $("sort").value;
    filtered.sort((a, b) =>
      sort === "name"
        ? a.name.localeCompare(b.name, "vi")
        : (sort === "capacity-desc"
            ? b.capacity - a.capacity
            : a.capacity - b.capacity) || a.name.localeCompare(b.name, "vi"),
    );
    $("room-list").append(...filtered.map(card));
    $("result-count").textContent = filtered.length;
    $("query-summary").textContent = currentSnapshot()
      ? `${rangeLabel(currentRange())} · ${count || "—"} người tham dự`
      : "Chọn khung giờ và nhấn Tìm phòng trống để cập nhật tình trạng phòng.";
    $("empty-state").hidden = filtered.some((room) => !reason(room));
    $("empty-message").textContent = !count
      ? participantError ||
        "Chưa nhận danh sách người tham dự từ US01. Hãy chọn người tại US01 rồi tìm phòng."
      : !rooms.length
        ? "Danh sách phòng hiện chưa có dữ liệu."
        : "Thử đổi thời gian hoặc bộ lọc. Nếu cần đổi người tham dự, hãy quay lại US01.";
    renderSelection();
    if ($("room-detail").open) renderDetail();
  }
  async function check() {
    const request = ++generation;
    snapshot = null;
    error("");
    const range = currentRange();
    if (!range) {
      error(
        "Chọn ngày hợp lệ và giờ kết thúc sau giờ bắt đầu.",
        $("date").value ? "end-time" : "date",
      );
      loading = false;
      render();
      return;
    }
    if (range.start <= Date.now()) {
      error("Ngày giờ bắt đầu phải nằm trong tương lai.", "start-time");
      loading = false;
      render();
      return;
    }
    try {
      people();
    } catch (failure) {
      error(failure.message, "people");
      loading = false;
      render();
      return;
    }
    loading = true;
    render();
    try {
      const result = await shared.check(range, people());
      if (request !== generation || currentRange()?.key !== range.key) return;
      loadRooms();
      snapshot = result;
    } catch (failure) {
      if (request === generation) error(failure.message);
    } finally {
      if (request === generation) {
        loading = false;
        render();
      }
    }
  }
  function scheduleCheck() {
    clearTimeout(checkTimer);
    checkTimer = setTimeout(check, 280);
  }
  function renderDetail() {
    const room = rooms.find((room) => room.id === detailId);
    if (!room) {
      $("room-detail").close();
      return;
    }
    $("detail-title").textContent = room.name;
    $("detail-location").textContent = room.location || "Chưa cập nhật vị trí";
    $("detail-status").replaceWith(
      Object.assign(badge(room), { id: "detail-status" }),
    );
    $("detail-capacity").textContent = `Sức chứa tối đa ${room.capacity} người`;
    $("detail-devices").replaceChildren(...devices(room).childNodes);
    const range = currentRange();
    $("detail-range").textContent = range
      ? rangeLabel(range)
      : "Chưa có khung giờ hợp lệ";
    $("detail-schedule").replaceChildren();
    let bookings = [];
    try {
      if (range)
        bookings = (snapshot?.meetings || []).filter(
          (meeting) =>
            String(meeting.roomId) === String(room.id) &&
            meeting.status !== "CANCELLED" &&
            new Intl.DateTimeFormat("sv-SE", {
              timeZone: "Asia/Ho_Chi_Minh",
              year: "numeric",
              month: "2-digit",
              day: "2-digit",
            }).format(new Date(meeting.startTime)) === range.date,
        );
    } catch {
      $("detail-schedule").append(
        node("p", "Chưa đọc được lịch phòng.", "schedule-empty"),
      );
    }
    const clock = (value) =>
      new Intl.DateTimeFormat("en-GB", {
        timeZone: "Asia/Ho_Chi_Minh",
        hour: "2-digit",
        minute: "2-digit",
        hourCycle: "h23",
      }).format(new Date(value));
    bookings.sort((a, b) => Date.parse(a.startTime) - Date.parse(b.startTime));
    for (const booking of bookings) {
      const row = node("div", undefined, "schedule-item");
      row.append(
        node(
          "strong",
          `${clock(booking.startTime)} – ${clock(booking.endTime)}`,
        ),
        node("span", "Đã được đặt"),
      );
      $("detail-schedule").append(row);
    }
    if (!bookings.length && !$("detail-schedule").childNodes.length)
      $("detail-schedule").append(
        node("p", "Chưa có lịch đặt trong ngày này.", "schedule-empty"),
      );
    let count = 0;
    try {
      count = people();
    } catch {}
    const past = range && range.start <= Date.now();
    const allowed = !reason(room);
    $("select-detail").disabled = !allowed || selecting || loading;
    $("detail-message").textContent = reason(room);
  }
  function openDetail(id) {
    detailId = id;
    renderDetail();
    $("room-detail").showModal();
  }
  async function selectRoom(id) {
    if (selecting || loading) return;
    const range = currentRange(),
      room = rooms.find((room) => room.id === id);
    let count;
    try {
      count = people();
    } catch (failure) {
      error(failure.message, "people");
      return;
    }
    if (!range || !room || !free(room) || room.capacity < count) {
      toast("Thông tin đã thay đổi. Vui lòng tìm phòng lại.");
      return;
    }
    if (range.start <= Date.now()) {
      toast("Chọn khung giờ trong tương lai để tạo lịch họp.");
      return;
    }
    const request = generation;
    selecting = true;
    render();
    try {
      const result = await shared.check(range, people()),
        latest = result.rooms.find((item) => String(item.id) === id);
      if (request !== generation || currentRange()?.key !== range.key) return;
      snapshot = result;
      loadRooms();
      if (
        !latest ||
        !latest.isAvailable ||
        latest.capacity < people() ||
        latest.reason
      ) {
        toast("Phòng vừa thay đổi hoặc đã được đặt. Vui lòng chọn phòng khác.");
        loadRooms();
        return;
      }
      const draft = {
        roomId: id,
        roomName: latest.name,
        date: range.date,
        startTime: range.startTime,
        endTime: range.endTime,
        participantCount: people(),
        participantIds: [...participantContext.participantIds],
        organizerId: participantContext.organizerId,
        organizerAttends: participantContext.organizerAttends,
      };
      try {
        sessionStorage.setItem("meeting.us07.selection", JSON.stringify(draft));
      } catch {
        throw Error(
          "Không thể chuẩn bị thông tin chuyển sang US01 vì bộ nhớ phiên không khả dụng.",
        );
      }
      selectedId = id;
      if ($("room-detail").open) $("room-detail").close();
      window.dispatchEvent(
        new CustomEvent("us07-room-selected", {
          detail: structuredClone(draft),
        }),
      );
      if (!$("return-us01").hidden) {
        location.assign($("return-us01").href);
      } else {
        toast(
          "Đã chuẩn bị phòng và thời gian để chuyển sang US01. Chưa có địa chỉ US01 hợp lệ để quay lại.",
        );
      }
    } catch (failure) {
      toast(failure.message);
    } finally {
      selecting = false;
      render();
    }
  }
  function clearSecondary() {
    $("search").value = "";
    $("location").value = "";
    $("availability-filter").value = "all";
    $("sort").value = "name";
    document
      .querySelectorAll('[name="device"]')
      .forEach((input) => (input.checked = false));
    render();
  }
  function defaults() {
    const now = new Date(Date.now() + 30 * 60000);
    const parts = Object.fromEntries(
      new Intl.DateTimeFormat("sv-SE", {
        timeZone: "Asia/Ho_Chi_Minh",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        hourCycle: "h23",
      })
        .formatToParts(now)
        .filter((part) => part.type !== "literal")
        .map((part) => [part.type, part.value]),
    );
    let date = `${parts.year}-${parts.month}-${parts.day}`,
      minute =
        Number(parts.hour) * 60 + Math.ceil(Number(parts.minute) / 30) * 30;
    if (minute < 9 * 60) minute = 9 * 60;
    if (minute > 17 * 60) {
      date = new Date(Date.parse(date + "T12:00:00+07:00") + 86400000)
        .toISOString()
        .slice(0, 10);
      minute = 9 * 60;
    }
    const clock = (value) =>
      String(Math.floor(value / 60)).padStart(2, "0") +
      ":" +
      String(value % 60).padStart(2, "0");
    const params = new URLSearchParams(location.search);
    $("date").value = params.get("date") || date;
    $("start-time").value = params.get("startTime") || clock(minute);
    $("end-time").value = params.get("endTime") || clock(minute + 60);
  }
  $("availability-form").addEventListener("submit", (event) => {
    event.preventDefault();
    check();
  });
  for (const id of ["date", "start-time", "end-time"]) {
    for (const event of ["input", "change"])
      $(id).addEventListener(event, scheduleCheck);
  }
  for (const id of ["search", "location", "sort", "availability-filter"])
    $(id).addEventListener(id === "search" ? "input" : "change", render);
  document
    .querySelectorAll('[name="device"]')
    .forEach((input) => input.addEventListener("change", render));
  $("reset-filters").addEventListener("click", () => {
    clearSecondary();
    error("");
  });
  $("empty-reset").addEventListener("click", clearSecondary);
  for (const [id, list] of [
    ["view-grid", false],
    ["view-list", true],
  ])
    $(id).addEventListener("click", () => {
      $("room-list").classList.toggle("list-view", list);
      $("view-grid").setAttribute("aria-pressed", String(!list));
      $("view-list").setAttribute("aria-pressed", String(list));
    });
  $("continue-selection").addEventListener("click", () =>
    selectRoom(selectedId),
  );
  ["building", "room-type"].forEach((id) =>
    $(id).addEventListener("change", render),
  );
  $("reset-all").addEventListener("click", () => {
    defaults();
    $("building").value = "";
    $("room-type").value = "";
    selectedId = null;
    clearSecondary();
    check();
  });
  $("select-detail").addEventListener("click", () => selectRoom(detailId));
  for (const id of ["close-detail", "cancel-detail"])
    $(id).addEventListener("click", () => $("room-detail").close());
  $("help").addEventListener("click", () => $("help-dialog").showModal());
  $("close-help").addEventListener("click", () => $("help-dialog").close());
  $("notifications").addEventListener("click", () =>
    toast("Bạn chưa có thông báo mới."),
  );
  function toggleMenu() {
    const mobile = matchMedia("(max-width: 800px)").matches;
    document.body.classList.toggle(mobile ? "menu-open" : "menu-closed");
    const open = mobile
      ? document.body.classList.contains("menu-open")
      : !document.body.classList.contains("menu-closed");
    $("menu-toggle").setAttribute("aria-expanded", String(open));
    $("menu-overlay").hidden = !open || !mobile;
  }
  $("menu-toggle").addEventListener("click", toggleMenu);
  $("sidebar-close").addEventListener("click", toggleMenu);
  $("nav-create-meeting").addEventListener("click", () => {
    if (selectedId) selectRoom(selectedId);
    else toast("Hãy chọn một phòng phù hợp trước khi chuyển sang tạo lịch.");
  });
  $("profile-role").textContent = "US07";
  $("menu-overlay").addEventListener("click", toggleMenu);
  $("menu-toggle").setAttribute(
    "aria-expanded",
    String(!matchMedia("(max-width: 800px)").matches),
  );
  window.addEventListener("storage", (event) => {
    if (event.key === shared.PARTICIPANTS_KEY) {
      loadParticipants();
      check();
    }
  });
  $("role-notice").textContent =
    "Trạng thái phòng được đọc trực tiếp từ API và kiểm tra lại trước khi chuyển về US01. Việc chọn phòng tại đây chưa tạo cuộc họp trong database.";
  // Receive selected account IDs from US01 without touching its form draft.
  window.US07Participants = {
    setSelection(value) {
      const next = shared.participantSelection(value);
      try {
        sessionStorage.setItem(shared.PARTICIPANTS_KEY, JSON.stringify(next));
        sessionStorage.removeItem("meeting.us07.selection");
      } catch {
        throw Error(
          "Không thể giữ danh sách người tham dự trong phiên trình duyệt.",
        );
      }
      participantContext = next;
      syncParticipantQuery(next);
      participantError = "";
      renderParticipants();
      selectedId = null;
      check();
    },
    getSelection() {
      return participantContext
        ? JSON.parse(JSON.stringify(participantContext))
        : null;
    },
  };
  window.addEventListener("us01-participants-changed", (event) => {
    try {
      window.US07Participants.setSelection(event.detail);
    } catch (failure) {
      participantContext = null;
      participantError = failure.message;
      renderParticipants();
      check();
    }
  });
  // Show a return link only when US01 actually opened this page; never invent a missing target.
  try {
    const previous = new URL(document.referrer);
    if (
      previous.origin === location.origin &&
      /\/(create-meeting|createe-meeting)\.html$/.test(previous.pathname)
    ) {
      $("return-us01").href = previous.href;
      $("return-us01").hidden = false;
    }
  } catch {}
  preserveQuery();
  loadRooms();
  loadParticipants();
  defaults();
  check();
})();
