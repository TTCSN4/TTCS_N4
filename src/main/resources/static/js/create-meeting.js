"use strict";
(() => {
  // Replace this provider with an API adapter later. No HTTP or persistence in demo mode.
  const demoCatalog = {
    rooms: [
      {
        id: "A101",
        name: "Phòng Hoa Sen · A101",
        capacity: 4,
        status: "AVAILABLE",
      },
      {
        id: "A102",
        name: "Phòng Hướng Dương · A102",
        capacity: 10,
        status: "AVAILABLE",
      },
      {
        id: "B201",
        name: "Phòng Đại Hội · B201",
        capacity: 30,
        status: "AVAILABLE",
      },
      {
        id: "B202",
        name: "Phòng Sáng Tạo · B202",
        capacity: 12,
        status: "AVAILABLE",
      },
      {
        id: "C301",
        name: "Phòng Trúc · C301",
        capacity: 20,
        status: "MAINTENANCE",
      },
    ],
    people: [
      {
        id: "u1",
        name: "Nguyễn Văn An",
        email: "an.nguyen@example.com",
        department: "Kỹ thuật",
      },
      {
        id: "u2",
        name: "Trần Thị Bình",
        email: "binh.tran@example.com",
        department: "Nhân sự",
      },
      {
        id: "u3",
        name: "Lê Minh Châu",
        email: "chau.le@example.com",
        department: "Kế toán",
      },
      {
        id: "u4",
        name: "Phạm Quốc Dũng",
        email: "dung.pham@example.com",
        department: "Kỹ thuật",
      },
      {
        id: "u5",
        name: "Hoàng Thu Hà",
        email: "ha.hoang@example.com",
        department: "Kinh doanh",
      },
      {
        id: "u6",
        name: "Đỗ Minh Huy",
        email: "huy.do@example.com",
        department: "Kinh doanh",
      },
      {
        id: "u7",
        name: "Vũ Ngọc Linh",
        email: "linh.vu@example.com",
        department: "Nhân sự",
      },
      {
        id: "u8",
        name: "Bùi Anh Tuấn",
        email: "tuan.bui@example.com",
        department: "Kỹ thuật",
      },
    ],
    equipment: [
      { id: "projector", name: "Máy chiếu", available: 2 },
      { id: "whiteboard", name: "Bảng trắng", available: 3 },
      { id: "microphone", name: "Micro", available: 6 },
      { id: "camera", name: "Camera hội nghị", available: 0 },
    ],
  };
  function roomIssue(room, count) {
    if (!room) return "Chọn phòng họp hợp lệ.";
    if (room.status === "UNCHECKED")
      return "Chọn ngày và giờ hợp lệ để kiểm tra.";
    if (room.status === "BOOKED") return "Phòng đã được đặt.";
    if (room.status === "MAINTENANCE") return "Phòng đang bảo trì.";
    if (room.status !== "AVAILABLE") return "Phòng không khả dụng.";
    if (!Number.isInteger(room.capacity) || room.capacity < count)
      return "Không đủ sức chứa (" + count + "/" + room.capacity + " người).";
    return "";
  }
  function equipmentIssue(items, inventory) {
    const used = new Set();
    for (const item of items) {
      const stock = inventory.find((entry) => entry.id === item.id);
      if (
        !stock ||
        used.has(item.id) ||
        !Number.isInteger(item.quantity) ||
        item.quantity < 0 ||
        item.quantity > stock.available
      ) {
        return "Số lượng thiết bị phải là số nguyên từ 0 đến số lượng còn khả dụng.";
      }
      used.add(item.id);
    }
    return "";
  }
  function attendeeCount(ids, organizerId) {
    const unique = new Set(ids);
    if (organizerId) unique.add(organizerId);
    return unique.size;
  }
  function validateMeeting(meeting, catalog, now = Date.now()) {
    const errors = {};
    if (
      !meeting.title ||
      !meeting.title.trim() ||
      meeting.title.trim().length > 120
    )
      errors.meetingTitle = "Nhập tên cuộc họp từ 1 đến 120 ký tự.";
    if (!Number.isFinite(meeting.start) || meeting.start <= now)
      errors.meetingDate = "Chọn ngày và giờ bắt đầu hợp lệ trong tương lai.";
    if (!meeting.startTime) errors.startTime = "Chọn giờ bắt đầu.";
    if (!Number.isFinite(meeting.end) || meeting.end <= meeting.start)
      errors.endTime = "Thời gian kết thúc phải sau thời gian bắt đầu.";
    const ids = Array.isArray(meeting.participantIds)
      ? meeting.participantIds
      : [];
    if (
      !ids.length ||
      new Set(ids).size !== ids.length ||
      ids.some((id) => !catalog.people.some((person) => person.id === id))
    )
      errors.participants =
        "Chọn ít nhất một người tham dự hợp lệ, không trùng lặp.";
    const issue = roomIssue(
      catalog.rooms.find((room) => room.id === meeting.roomId),
      attendeeCount(ids, meeting.organizerId || currentUser.id),
    );
    if (issue) errors.meetingRoom = issue;
    const selectedRoom = catalog.rooms.find(
      (room) => room.id === meeting.roomId,
    );
    if (
      selectedRoom &&
      attendeeCount(ids, meeting.organizerId || currentUser.id) >
        selectedRoom.capacity
    )
      errors.participants =
        "Đã chọn " +
        attendeeCount(ids, meeting.organizerId || currentUser.id) +
        " người (tính cả chủ trì), nhưng phòng chỉ chứa " +
        selectedRoom.capacity +
        " người. Bỏ bớt người hoặc chọn phòng lớn hơn.";
    const stockIssue = Array.isArray(meeting.equipment)
      ? equipmentIssue(meeting.equipment, catalog.equipment)
      : "Danh sách thiết bị không hợp lệ.";
    if (stockIssue) errors.equipment = stockIssue;
    if ((meeting.notes || "").length > 2000)
      errors.meetingNotes = "Mô tả không quá 2.000 ký tự.";
    return errors;
  }
  // Frontend request contract; adapt field names when the real backend contract is available.
  function buildCreateRequest(meeting) {
    return {
      title: meeting.title.trim(),
      startTime: new Date(meeting.start).toISOString(),
      endTime: new Date(meeting.end).toISOString(),
      roomId: meeting.roomId,
      participantIds: [...meeting.participantIds],
      organizerId: meeting.organizerId || currentUser.id,
      attendeeCount: attendeeCount(
        meeting.participantIds,
        meeting.organizerId || currentUser.id,
      ),
      equipment: meeting.equipment
        .filter((item) => item.quantity > 0)
        .map((item) => ({ equipmentId: item.id, quantity: item.quantity })),
      notes: meeting.notes || "",
      recurrence: meeting.recurrence
        ? JSON.parse(JSON.stringify(meeting.recurrence))
        : null,
    };
  }
  function overlaps(a, b) {
    return a.start < b.end && b.start < a.end;
  }
  function availability(base, bookings, start, end) {
    const valid = Number.isFinite(start) && Number.isFinite(end) && end > start;
    const active = valid
      ? bookings.filter(
          (b) => b.status !== "CANCELLED" && overlaps(b, { start, end }),
        )
      : [];
    return {
      ...base,
      validWindow: valid,
      rooms: base.rooms.map((r) => ({
        ...r,
        status:
          r.status !== "AVAILABLE"
            ? r.status
            : !valid
              ? "UNCHECKED"
              : active.some((b) => b.roomId === r.id)
                ? "BOOKED"
                : "AVAILABLE",
      })),
      equipment: base.equipment.map((item) => {
        // Peak concurrent usage in half-open intervals [start, end).
        const points = [];
        for (const b of active) {
          const q = b.equipment.find((e) => e.id === item.id)?.quantity || 0;
          if (q)
            points.push(
              { time: Math.max(start, b.start), delta: q },
              { time: Math.min(end, b.end), delta: -q },
            );
        }
        points.sort((a, b) => a.time - b.time || a.delta - b.delta);
        let used = 0,
          peak = 0;
        for (const p of points) {
          used += p.delta;
          peak = Math.max(peak, used);
        }
        return {
          ...item,
          total: item.available,
          available: valid ? Math.max(0, item.available - peak) : 0,
        };
      }),
    };
  }
  const tomorrow = new Date(Date.now() + 7 * 3600000 + 86400000)
    .toISOString()
    .slice(0, 10);
  const at = (time) => Date.parse(tomorrow + "T" + time + ":00+07:00");
  const reservations = [
    {
      id: "seed-1",
      roomId: "A101",
      start: at("09:00"),
      end: at("10:00"),
      equipment: [
        { id: "projector", quantity: 1 },
        { id: "microphone", quantity: 2 },
      ],
    },
    {
      id: "seed-2",
      roomId: "B202",
      start: at("09:00"),
      end: at("11:00"),
      equipment: [
        { id: "projector", quantity: 1 },
        { id: "microphone", quantity: 2 },
      ],
    },
    {
      id: "seed-3",
      roomId: "A102",
      start: at("14:00"),
      end: at("15:00"),
      equipment: [{ id: "projector", quantity: 2 }],
    },
  ];
  const demoUsers = {
    ADMIN: {
      id: "u2",
      name: "Trần Thị Bình",
      role: "ADMIN",
      active: true,
    },
    PM: { id: "u1", name: "Nguyễn Văn An", role: "PM", active: true },
    MEMBER: {
      id: "u3",
      name: "Lê Minh Châu",
      role: "MEMBER",
      active: true,
    },
  };
  let currentUser = { ...demoUsers.PM };
  function canCreateMeeting(user) {
    return Boolean(
      user && user.active === true && ["ADMIN", "PM"].includes(user.role),
    );
  }
  function parseMeetingTime(date, time) {
    if (
      !/^\d{4}-\d{2}-\d{2}$/.test(date) ||
      !/^([01]\d|2[0-3]):[0-5]\d$/.test(time)
    )
      return NaN;
    const timestamp = Date.parse(date + "T" + time + ":00+07:00");
    if (!Number.isFinite(timestamp)) return NaN;
    return new Date(timestamp + 7 * 3600000).toISOString().slice(0, 16) ===
      date + "T" + time
      ? timestamp
      : NaN;
  }
  const provider = {
    getCurrentUser() {
      return { ...currentUser };
    },
    setDemoRole(role) {
      currentUser = {
        ...(demoUsers[role] || { role: "UNKNOWN", active: false }),
      };
    },
    async getCatalog() {
      return JSON.parse(JSON.stringify(demoCatalog));
    },
    getAvailability(start, end) {
      return availability(demoCatalog, reservations, start, end);
    },
    async simulateCreate(meeting) {
      meeting = { ...meeting, organizerId: currentUser.id };
      if (!canCreateMeeting(currentUser))
        throw Error(
          "Bạn không có quyền tạo lịch họp. Chỉ Admin và PM được thực hiện thao tác này.",
        );
      const errors = validateMeeting(
        meeting,
        this.getAvailability(meeting.start, meeting.end),
      );
      if (Object.keys(errors).length)
        throw Error(Object.values(errors).join(" "));
      // Prepare every part before changing any session state. A preparation failure commits nothing.
      const request = buildCreateRequest(meeting);
      const id = "demo-" + (this.sequence + 1);
      const result = JSON.parse(
        JSON.stringify({
          ...meeting,
          id,
          simulated: true,
          request,
          roomBooking: {
            meetingId: id,
            roomId: request.roomId,
            startTime: request.startTime,
            endTime: request.endTime,
          },
          attendeeCount: request.attendeeCount,
          participantRecords: request.participantIds.map((userId) => ({
            meetingId: id,
            userId,
          })),
          equipmentBookings: request.equipment.map((item) => ({
            meetingId: id,
            ...item,
            startTime: request.startTime,
            endTime: request.endTime,
          })),
        }),
      );
      const response = JSON.parse(JSON.stringify(result));
      reservations.push(result);
      this.sequence += 1;
      return response;
    },
    cancelDemo(id) {
      const booking = reservations.find((b) => b.id === id && b.simulated);
      if (booking) booking.status = "CANCELLED";
    },
    sampleDate: tomorrow,
    sequence: 0,
  };
  globalThis.US01Demo = {
    provider,
    roomIssue,
    equipmentIssue,
    validateMeeting,
    availability,
    overlaps,
    buildCreateRequest,
    attendeeCount,
    canCreateMeeting,
    parseMeetingTime,
  };
  if (typeof document === "undefined") return;

  const $ = (id) => document.getElementById(id),
    form = $("createMeetingForm");
  if (!form) return;
  let catalog = { rooms: [], people: [], equipment: [] },
    schedules = [],
    busy = false,
    ready = false;
  const roleQuery =
    typeof location !== "undefined"
      ? new URLSearchParams(location.search).get("role")
      : null;
  if (roleQuery !== null) provider.setDemoRole(roleQuery.toUpperCase());
  const user = provider.getCurrentUser();
  if ($("currentUserName"))
    $("currentUserName").textContent = user.name || "Tài khoản mẫu";
  if ($("currentUserRole"))
    $("currentUserRole").textContent =
      { ADMIN: "Admin", PM: "PM", MEMBER: "Member" }[user.role] ||
      "Không có quyền";
  function updatePermission() {
    const allowed = canCreateMeeting(provider.getCurrentUser());
    $("permissionStatus").hidden = allowed;
    $("permissionStatus").textContent = allowed
      ? ""
      : "Bạn không có quyền tạo lịch họp. Chỉ Admin và PM được tạo lịch; Member chỉ được xem biểu mẫu.";
    return allowed;
  }
  const selectedPeople = new Set();
  const totalPeople = () =>
    attendeeCount([...selectedPeople], provider.getCurrentUser().id);
  const days = [
    "Thứ 2",
    "Thứ 3",
    "Thứ 4",
    "Thứ 5",
    "Thứ 6",
    "Thứ 7",
    "Chủ nhật",
  ];
  const fields = [
    "meetingTitle",
    "meetingDate",
    "meetingRoom",
    "startTime",
    "endTime",
    "meetingNotes",
    "participants",
    "equipment",
  ];
  const node = (tag, text, className) => {
    const element = document.createElement(tag);
    if (text !== undefined) element.textContent = text;
    if (className) element.className = className;
    return element;
  };
  function status(text, error = false) {
    $("formStatus").hidden = false;
    $("formStatus").dataset.error = String(error);
    $("formStatus").textContent = text;
  }
  function quantities() {
    return catalog.equipment.map((item) => ({
      id: item.id,
      quantity: Number(
        $("quantity-" + item.id).value === ""
          ? NaN
          : $("quantity-" + item.id).value,
      ),
    }));
  }
  function payload() {
    const date = $("meetingDate").value,
      startTime = $("startTime").value,
      endTime = $("endTime").value;
    return {
      title: $("meetingTitle").value.trim(),
      roomId: $("meetingRoom").value,
      startTime,
      endTime,
      start: parseMeetingTime(date, startTime),
      end: parseMeetingTime(date, endTime),
      organizerId: provider.getCurrentUser().id,
      participantIds: [...selectedPeople],
      equipment: quantities(),
      notes: $("meetingNotes").value.trim(),
      recurrence: recurrence(),
    };
  }
  function recurrence() {
    const frequency = $("repeatFrequency").value;
    if (frequency === "NONE") return null;
    return {
      frequency,
      interval: Number($("repeatInterval").value),
      weekdays:
        frequency === "WEEKLY"
          ? [...form.querySelectorAll("[name=weekday]:checked")].map((el) =>
              Number(el.value),
            )
          : null,
      monthDay: frequency === "MONTHLY" ? Number($("monthDay").value) : null,
      until:
        $("repeatEndMode").value === "UNTIL"
          ? $("repeatUntil").value || null
          : null,
      count:
        $("repeatEndMode").value === "COUNT"
          ? Number($("repeatCount").value)
          : null,
    };
  }
  function repeatError(r) {
    if (!r) return "";
    if (!Number.isInteger(r.interval) || r.interval < 1 || r.interval > 52)
      return "Khoảng cách lặp phải từ 1 đến 52.";
    if (r.frequency === "WEEKLY" && !r.weekdays.length)
      return "Chọn ít nhất một thứ trong tuần.";
    if (
      r.frequency === "MONTHLY" &&
      (!Number.isInteger(r.monthDay) || r.monthDay < 1 || r.monthDay > 31)
    )
      return "Ngày trong tháng phải từ 1 đến 31.";
    if (
      $("repeatEndMode").value === "COUNT" &&
      (!Number.isInteger(r.count) || r.count < 1 || r.count > 100)
    )
      return "Số cuộc họp phải từ 1 đến 100.";
    if (
      $("repeatEndMode").value === "UNTIL" &&
      (!r.until || r.until < $("meetingDate").value)
    )
      return "Ngày kết thúc phải từ ngày bắt đầu trở đi.";
    return "";
  }

  function renderRooms() {
    const selected = $("meetingRoom").value;
    $("meetingRoom").replaceChildren();
    const empty = node("option", "Chọn phòng họp");
    empty.value = "";
    $("meetingRoom").append(empty);
    for (const room of catalog.rooms) {
      const issue = roomIssue(room, totalPeople());
      const option = node(
        "option",
        room.name +
          " · " +
          room.capacity +
          " người · " +
          (issue || "Còn trống"),
      );
      option.value = room.id;
      option.disabled = Boolean(issue);
      $("meetingRoom").append(option);
    }
    const current = catalog.rooms.find((room) => room.id === selected);
    // Keep the chosen room visible when capacity is exceeded so the user can see and resolve the error.
    $("meetingRoom").value =
      current && current.status === "AVAILABLE" ? selected : "";
    if (selected && !$("meetingRoom").value) {
      $("meetingRoomError").textContent =
        "Phòng trước đó không đủ sức chứa hoặc không khả dụng. Vui lòng chọn phòng khác.";
      $("meetingRoom").setAttribute("aria-invalid", "true");
    }
  }
  function normalize(value) {
    return value
      .toLocaleLowerCase("vi")
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/đ/g, "d");
  }
  function filteredPeople() {
    const query = normalize($("participantSearch").value.trim());
    return catalog.people.filter((person) =>
      normalize(person.name + " " + person.email).includes(query),
    );
  }
  function renderPeople() {
    const query = normalize($("participantSearch").value.trim());
    $("participantOptions").replaceChildren();
    const matches = filteredPeople();
    $("selectAllParticipants").disabled = matches.length === 0;
    $("clearParticipants").disabled = selectedPeople.size === 0;
    for (const person of matches) {
      const label = node("label", undefined, "participant-option"),
        input = node("input");
      input.type = "checkbox";
      input.value = person.id;
      input.checked = selectedPeople.has(person.id);
      input.addEventListener("change", () => {
        input.checked
          ? selectedPeople.add(person.id)
          : selectedPeople.delete(person.id);
        $("participantsError").textContent = "";
        refreshAvailability();
        $("clearParticipants").disabled = selectedPeople.size === 0;
      });
      const info = node("span");
      info.append(
        node("strong", person.name),
        node("small", person.email + " · " + person.department),
      );
      label.append(input, info);
      $("participantOptions").append(label);
    }
    if (!matches.length)
      $("participantOptions").append(
        node("p", "Không tìm thấy người phù hợp.", "hint"),
      );
  }
  function renderEquipment(previous = new Map()) {
    $("equipmentOptions").replaceChildren();
    for (const item of catalog.equipment) {
      const row = node("div", undefined, "equipment-quantity"),
        info = node("div");
      info.append(
        node("strong", item.name),
        node(
          "small",
          "Còn " +
            item.available +
            (item.available === 0
              ? " · Không còn trong khung giờ"
              : " thiết bị"),
        ),
      );
      const label = node("label", "Số lượng đặt"),
        input = node("input");
      input.id = "quantity-" + item.id;
      input.type = "number";
      input.min = "0";
      input.max = String(item.available);
      input.step = "1";
      input.value = previous.has(item.id) ? previous.get(item.id) : "0";
      input.disabled = item.available === 0 && Number(input.value) === 0;
      input.setAttribute("aria-label", "Số lượng đặt " + item.name);
      input.setAttribute("aria-describedby", "equipmentError");
      function check() {
        const value = Number(input.value);
        if (input.value !== "" && Number.isFinite(value))
          input.value = String(
            Math.max(0, Math.min(item.available, Math.floor(value))),
          );
        const issue = equipmentIssue(quantities(), catalog.equipment);
        $("equipmentError").textContent = issue;
        input.setAttribute("aria-invalid", String(Boolean(issue)));
        summary();
      }
      input.addEventListener("input", check);
      input.addEventListener("change", check);
      label.append(input);
      row.append(info, label);
      $("equipmentOptions").append(row);
    }
  }
  function refreshAvailability() {
    const previous = new Map(
      catalog.equipment.map((item) => [
        item.id,
        $("quantity-" + item.id)?.value ?? "0",
      ]),
    );
    const date = $("meetingDate").value;
    const start = parseMeetingTime(date, $("startTime").value);
    const end = parseMeetingTime(date, $("endTime").value);
    catalog = provider.getAvailability(start, end);
    renderRooms();
    renderEquipment(previous);
    $("equipmentError").textContent = equipmentIssue(
      quantities(),
      catalog.equipment,
    );
    $("availabilityStatus").textContent = !catalog.validWindow
      ? "Chọn ngày, giờ bắt đầu và kết thúc hợp lệ để kiểm tra."
      : "Đã kiểm tra dữ liệu mẫu theo khung giờ: " +
        catalog.rooms.filter((r) => !roomIssue(r, totalPeople())).length +
        " phòng phù hợp. Thiết bị hiển thị số lượng còn trong toàn bộ khung giờ.";
    summary();
  }
  function updateParticipantCapacity() {
    const room = catalog.rooms.find(
      (entry) => entry.id === $("meetingRoom").value,
    );
    const exceeded = Boolean(room && totalPeople() > room.capacity);
    const message = exceeded
      ? "Đã chọn " +
        totalPeople() +
        " người (tính cả chủ trì), nhưng phòng " +
        room.name +
        " chỉ chứa " +
        room.capacity +
        " người. Bỏ bớt người hoặc chọn phòng lớn hơn."
      : "";
    $("participantsError").textContent = message;
    $("participants").setAttribute("aria-invalid", String(exceeded));
    $("participantSearch").setAttribute("aria-invalid", String(exceeded));
    $("participantCapacity").textContent = room
      ? "Sức chứa phòng: " +
        room.capacity +
        " người · Tổng có mặt (tính cả chủ trì): " +
        totalPeople()
      : "Chủ trì: " +
        (provider.getCurrentUser().name || "Tài khoản hiện tại") +
        ". Tổng có mặt: " +
        totalPeople() +
        " người; chỉ cộng chủ trì nếu chưa được chọn.";
    $("submitBtn").disabled = busy || !ready || exceeded || !updatePermission();
    updatePermission();
    if (room && room.status === "AVAILABLE") {
      $("meetingRoomError").textContent = exceeded
        ? "Số người tham dự vượt sức chứa phòng."
        : "";
      $("meetingRoom").setAttribute("aria-invalid", String(exceeded));
    }
  }
  function summary() {
    const p = payload();
    $("participantCount").textContent = selectedPeople.size + " người đã chọn";
    if ($("summaryCount"))
      $("summaryCount").textContent = selectedPeople.size + " người";
    if ($("summaryTitle"))
      $("summaryTitle").textContent = p.title || "Cuộc họp mới của bạn";
    if ($("summaryDate"))
      $("summaryDate").textContent = $("meetingDate").value || "Chưa chọn";
    if ($("summaryTime"))
      $("summaryTime").textContent =
        p.startTime && p.endTime
          ? p.startTime + " – " + p.endTime
          : "Chưa chọn";
    if ($("summaryRoom"))
      $("summaryRoom").textContent =
        catalog.rooms.find((room) => room.id === p.roomId)?.name || "Chưa chọn";
    const minutes = (p.end - p.start) / 60000;
    $("summaryDuration").textContent =
      minutes > 0 ? minutes + " phút" : "Chưa chọn";
    if ($("summaryEquipment"))
      $("summaryEquipment").textContent =
        p.equipment
          .filter((item) => item.quantity > 0)
          .map(
            (item) =>
              catalog.equipment.find((entry) => entry.id === item.id).name +
              " × " +
              item.quantity,
          )
          .join(", ") || "Chưa chọn";
    $("repeatStart").value = $("meetingDate").value;
    const r = p.recurrence;
    $("repeatUnit").textContent =
      r?.frequency === "MONTHLY" ? "tháng" : "tuần vào các ngày";
    $("repeatConfig").hidden = !r;
    $("weeklyConfig").hidden = !r || r.frequency !== "WEEKLY";
    $("monthlyConfig").hidden = !r || r.frequency !== "MONTHLY";
    $("countConfig").hidden = $("repeatEndMode").value !== "COUNT";
    $("untilConfig").hidden = $("repeatEndMode").value !== "UNTIL";
    $("repeatUntil").min = $("meetingDate").value;
    if ($("repeatSummary"))
      $("repeatSummary").textContent = r
        ? "Mô phỏng lặp mỗi " +
          (r.interval || "…") +
          " " +
          (r.frequency === "WEEKLY"
            ? "tuần vào " +
              (r.weekdays.map((day) => days[day - 1]).join(", ") ||
                "các thứ chưa chọn")
            : "tháng vào ngày " + (r.monthDay || "…")) +
          "; " +
          (r.until ? "đến " + r.until : (r.count || "…") + " cuộc họp") +
          "."
        : "Cuộc họp không lặp lại.";
    $("repeatError").textContent = repeatError(r);
    updateParticipantCapacity();
  }
  function showErrors(errors) {
    for (const id of fields) {
      $(id + "Error").textContent = errors[id] || "";
      const control = $(id);
      if (control)
        control.setAttribute("aria-invalid", String(Boolean(errors[id])));
    }
    const first = Object.keys(errors)[0];
    if (first)
      (first === "participants"
        ? $("participantSearch")
        : first === "equipment"
          ? $("equipmentOptions").querySelector("input:not(:disabled)")
          : $(first)
      )?.focus();
  }
  function renderSchedules() {
    if (!$("savedList")) return;
    $("meetingBadge").textContent = String(schedules.length);
    $("savedList").replaceChildren();
    if (!schedules.length)
      $("savedList").append(
        node("p", "Chưa có lịch mô phỏng trong phiên này."),
      );
    for (const item of schedules) {
      const row = node("article", undefined, "saved-item"),
        info = node("div");
      info.append(
        node("strong", item.title),
        node(
          "p",
          catalog.rooms.find((room) => room.id === item.roomId)?.name +
            " · " +
            new Date(item.start).toLocaleString("vi-VN", {
              timeZone: "Asia/Ho_Chi_Minh",
            }) +
            " – " +
            item.endTime +
            " · " +
            item.participantIds.length +
            " người · " +
            (item.recurrence ? "Có cấu hình lặp · " : "") +
            "Chỉ mô phỏng",
        ),
      );
      const details = node("details"),
        heading = node("summary", "Xem người tham dự và thiết bị");
      const people = item.participantIds
        .map(
          (id) => catalog.people.find((person) => person.id === id)?.name || id,
        )
        .join(", ");
      const equipment =
        item.equipment
          .filter((entry) => entry.quantity > 0)
          .map(
            (entry) =>
              (catalog.equipment.find((stock) => stock.id === entry.id)?.name ||
                entry.id) +
              " × " +
              entry.quantity,
          )
          .join(", ") || "Không đặt thiết bị";
      details.append(
        heading,
        node("p", "Người tham dự: " + people),
        node("p", "Thiết bị: " + equipment),
      );
      info.append(details);
      const cancel = node("button", "Hủy mô phỏng", "button secondary");
      cancel.type = "button";
      cancel.addEventListener("click", () => {
        if (busy || !confirm("Bỏ lịch mô phỏng này?")) return;
        provider.cancelDemo(item.id);
        schedules = schedules.filter((entry) => entry.id !== item.id);
        renderSchedules();
        refreshAvailability();
        status(
          "Đã bỏ lịch mô phỏng và hoàn trả phòng, thiết bị cho khung giờ tương ứng.",
        );
      });
      row.append(info, cancel);
      $("savedList").append(row);
    }
  }
  form.querySelectorAll("[name=frequencyChoice]").forEach((radio) =>
    radio.addEventListener("change", () => {
      $("repeatFrequency").value = radio.value;
      summary();
    }),
  );
  $("repeatStart").addEventListener("input", () => {
    $("meetingDate").value = $("repeatStart").value;
    summary();
  });
  $("selectAllParticipants").addEventListener("click", () => {
    filteredPeople().forEach((person) => selectedPeople.add(person.id));
    renderPeople();
    refreshAvailability();
  });
  $("clearParticipants").addEventListener("click", () => {
    selectedPeople.clear();
    renderPeople();
    refreshAvailability();
  });
  $("participantSearch").addEventListener("input", renderPeople);
  ["meetingDate", "startTime", "endTime"].forEach((id) => {
    ["input", "change"].forEach((event) =>
      $(id).addEventListener(event, () => {
        if (ready) refreshAvailability();
      }),
    );
  });
  $("repeatStart").addEventListener("input", () => {
    if (ready) refreshAvailability();
  });
  $("meetingRoom").addEventListener("change", () => {
    $("meetingRoomError").textContent = "";
    $("meetingRoom").setAttribute("aria-invalid", "false");
  });
  form.addEventListener("input", () => {
    if (ready) {
      $("formStatus").hidden = true;
      summary();
    }
  });
  form.addEventListener("change", () => {
    if (ready) summary();
  });
  $("resetBtn").addEventListener("click", () => {
    if (busy || !confirm("Xóa thông tin đang nhập?")) return;
    form.reset();
    selectedPeople.clear();
    $("repeatFrequency").value = "NONE";
    renderPeople();
    renderEquipment();
    refreshAvailability();
    showErrors({});
    $("formStatus").hidden = true;
    summary();
  });
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!updatePermission()) {
      status(
        "Bạn không có quyền tạo lịch họp. Chỉ Admin và PM được thực hiện thao tác này.",
        true,
      );
      return;
    }
    if (!ready || busy) return;
    refreshAvailability();
    const meeting = payload(),
      errors = validateMeeting(meeting, catalog);
    showErrors(errors);
    const repeatIssue = repeatError(meeting.recurrence);
    if (repeatIssue) {
      const disclosure = document.querySelector(".repeat-disclosure");
      if (disclosure) disclosure.open = true;
    }
    $("repeatError").textContent = repeatIssue;
    if (Object.keys(errors).length || repeatIssue) {
      status("Vui lòng kiểm tra các thông tin được đánh dấu.", true);
      if (!Object.keys(errors).length)
        $("repeatFrequency").value === "WEEKLY"
          ? form.querySelector("[name=weekday]").focus()
          : $("monthDay").focus();
      return;
    }
    busy = true;
    $("submitBtn").disabled = true;
    try {
      const result = await provider.simulateCreate(meeting);
      schedules.unshift(result);
      renderSchedules();
      form.reset();
      selectedPeople.clear();
      $("repeatFrequency").value = "NONE";
      renderPeople();
      renderEquipment();
      refreshAvailability();
      showErrors({});
      status(
        "Mô phỏng tạo lịch họp “" +
          meeting.title +
          "” thành công với " +
          result.attendeeCount +
          " người (tính cả chủ trì). Dữ liệu chỉ tồn tại trong phiên trang, không được lưu vào cơ sở dữ liệu." +
          (meeting.recurrence
            ? " Cấu hình lặp được giữ để xem trước; chưa tạo các lần họp thực tế."
            : ""),
      );
      $("formStatus").scrollIntoView({ behavior: "smooth", block: "center" });
    } catch (error) {
      status("Không thể mô phỏng: " + error.message, true);
    } finally {
      busy = false;
      updateParticipantCapacity();
    }
  });
  async function initialize() {
    $("submitBtn").disabled = true;
    try {
      catalog = await provider.getCatalog();
      renderPeople();
      renderEquipment();
      renderSchedules();
      ready = true;
      refreshAvailability();
      if ($("sampleScheduleHint"))
        $("sampleScheduleHint").textContent =
          "Lịch mẫu ngày " +
          provider.sampleDate +
          " (giờ Việt Nam): A101 đã đặt 09:00–10:00, B202 đã đặt 09:00–11:00; hai lịch dùng hết 2 máy chiếu lúc 09:00–10:00. A102 đã đặt 14:00–15:00 và dùng 2 máy chiếu.";
      status(
        "Chế độ mô phỏng: phòng, người tham dự và thiết bị dùng dữ liệu mẫu; không gọi API. Sức chứa được kiểm tra theo tổng số người, tính cả chủ trì nếu chưa có trong danh sách đã chọn.",
      );
    } catch (error) {
      status("Không tải được dữ liệu mẫu: " + error.message, true);
    } finally {
      updateParticipantCapacity();
    }
  }
  initialize();
})();
