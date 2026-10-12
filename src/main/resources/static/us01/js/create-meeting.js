"use strict";
(() => {
  const $ = (id) => document.getElementById(id),
    form = $("createMeetingForm");
  let catalog = { rooms: [], people: [], equipment: [], organizer: null, includeOrganizer: false },
    participants = [],
    equipment = new Set(),
    dirty = false;
  const node = (tag, text, cls) => {
    const el = document.createElement(tag);
    if (text != null) el.textContent = text;
    if (cls) el.className = cls;
    return el;
  };
  const normalize = (s) =>
    String(s || "")
      .toLocaleLowerCase("vi")
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/đ/g, "d");
  function message(text, error = false) {
    $("formStatus").hidden = false;
    $("formStatus").textContent = text;
    $("formStatus").dataset.error = String(error);
  }
  function countPeople() {
    return (
      participants.length +
      (catalog.includeOrganizer &&
      catalog.organizer &&
      !participants.some((p) => p.email?.toLowerCase() === catalog.organizer.email?.toLowerCase())
        ? 1
        : 0)
    );
  }
  function stamp(date, time) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !/^\d{2}:\d{2}$/.test(time)) return NaN;
    const value = Date.parse(date + "T" + time + ":00+07:00");
    if (
      !Number.isFinite(value) ||
      new Date(value + 7 * 3600000).toISOString().slice(0, 10) !== date
    )
      return NaN;
    return value;
  }
  function times() {
    return {
      start: stamp($("startDate").value, $("startTime").value),
      end: stamp($("endDate").value, $("endTime").value),
    };
  }
  function room() {
    return catalog.rooms.find((r) => String(r.id) === $("meetingRoom").value);
  }
  function fieldError(id, text) {
    $(id + "Error").textContent = text || "";
    $(id).setAttribute("aria-invalid", String(!!text));
    $(id).closest(".field").classList.toggle("invalid", !!text);
  }
  function validate({ requireRoom = true, requireTitle = true } = {}) {
    const { start, end } = times(),
      errors = {};
    if (requireTitle && !$("meetingTitle").value.trim())
      errors.meetingTitle = "Vui lòng nhập tiêu đề cuộc họp.";
    else if ($("meetingTitle").value.trim().length > 200)
      errors.meetingTitle = "Tiêu đề không quá 200 ký tự.";
    if (!Number.isFinite(start)) {
      if (!$("startDate").value) errors.startDate = "Chọn ngày bắt đầu.";
      else errors.startTime = "Chọn giờ bắt đầu hợp lệ.";
    } else if (start <= Date.now())
      errors.startDate = "Thời gian bắt đầu phải nằm trong tương lai.";
    if (!Number.isFinite(end)) {
      if (!$("endDate").value) errors.endDate = "Chọn ngày kết thúc.";
      else errors.endTime = "Chọn giờ kết thúc hợp lệ.";
    } else if (Number.isFinite(start) && end <= start)
      errors.endTime = "Thời gian kết thúc phải sau thời gian bắt đầu.";
    if (requireRoom) {
      const selected = room();
      if (!selected)
        errors.meetingRoom = catalog.rooms.length
          ? "Vui lòng chọn phòng họp."
          : "Chưa có dữ liệu phòng; sẽ kết nối API ở bước tiếp theo.";
      else if (selected.capacity < countPeople())
        errors.meetingRoom = "Phòng chỉ có sức chứa " + selected.capacity + " người.";
      else if (
        selected.available === false ||
        selected.status === "MAINTENANCE" ||
        selected.status === "INACTIVE"
      )
        errors.meetingRoom = "Phòng hiện không khả dụng.";
    }
    for (const id of [
      "meetingTitle",
      "startDate",
      "startTime",
      "endDate",
      "endTime",
      "meetingRoom",
    ])
      fieldError(id, errors[id]);
    const first = Object.keys(errors)[0];
    if (first) $(first).focus();
    return !first;
  }
  function preview() {
    $("titleCount").textContent = $("meetingTitle").value.length + "/200";
    $("descriptionCount").textContent = $("meetingDescription").value.length + "/500";
    $("notesCount").textContent = $("meetingNotes").value.length + "/500";
    $("previewTitle").textContent = $("meetingTitle").value.trim() || "–";
    $("previewDescription").textContent = $("meetingDescription").value.trim() || "–";
    $("previewNotes").textContent = $("meetingNotes").value.trim() || "–";
    const { start, end } = times();
    $("previewTime").textContent =
      Number.isFinite(start) && Number.isFinite(end)
        ? new Date(start).toLocaleString("vi-VN", {
            timeZone: "Asia/Ho_Chi_Minh",
            day: "2-digit",
            month: "2-digit",
            year: "numeric",
            hour: "2-digit",
            minute: "2-digit",
          }) +
          " – " +
          new Date(end).toLocaleString("vi-VN", {
            timeZone: "Asia/Ho_Chi_Minh",
            day: "2-digit",
            month: "2-digit",
            year: "numeric",
            hour: "2-digit",
            minute: "2-digit",
          })
        : "–";
    const r = room();
    $("previewRoom").textContent = r?.name || "–";
    $("roomCapacity").textContent = r ? r.capacity + " người" : "–";
    $("roomDevices").textContent =
      r && Array.isArray(r.equipmentNames) ? r.equipmentNames.join(", ") || "Chưa có dữ liệu" : "–";
    $("roomStatus").textContent = r?.statusLabel || "Chưa kiểm tra";
    const n = countPeople();
    $("previewPeople").textContent = n
      ? n +
        " người" +
        (catalog.includeOrganizer && catalog.organizer ? " (bao gồm người tổ chức)" : "")
      : "Chưa chọn";
    $("previewEquipment").textContent =
      catalog.equipment
        .filter((e) => equipment.has(String(e.id)))
        .map((e) => e.name)
        .join(", ") || "Chưa chọn";
  }
  function renderChips() {
    $("participantChips").replaceChildren();
    participants.forEach((person) => {
      const chip = node("span", null, "chip"),
        avatar = node(
          "b",
          (person.name || person.email)
            .trim()
            .split(/\s+/)
            .map((s) => s[0])
            .slice(0, 2)
            .join("")
            .toUpperCase(),
          "chip-avatar",
        ),
        remove = node("button", "×");
      remove.type = "button";
      remove.setAttribute("aria-label", "Bỏ " + (person.name || person.email));
      remove.addEventListener("click", () => {
        participants = participants.filter((p) => p !== person);
        dirty = true;
        renderChips();
        preview();
      });
      chip.append(avatar, node("span", person.name || person.email), remove);
      $("participantChips").append(chip);
    });
  }
  function addPerson(person) {
    const email = String(person.email || "").trim();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254) {
      $("participantError").textContent = "Nhập email hợp lệ hoặc chọn người trong danh sách.";
      return;
    }
    if (participants.some((p) => p.email.toLowerCase() === email.toLowerCase())) {
      $("participantError").textContent = "Người này đã có trong danh sách.";
      return;
    }
    const selected = room();
    if (selected && countPeople() + 1 > selected.capacity) {
      $("participantError").textContent = "Số người vượt sức chứa phòng. Chọn phòng khác trước.";
      return;
    }
    participants.push({ ...person, email });
    dirty = true;
    $("participantError").textContent = "";
    $("participantSearch").value = "";
    $("participantSuggestions").hidden = true;
    renderChips();
    preview();
  }
  $("participantSearch").addEventListener("input", () => {
    const q = normalize($("participantSearch").value);
    $("participantSuggestions").replaceChildren();
    if (!q) {
      $("participantSuggestions").hidden = true;
      return;
    }
    const matches = catalog.people
      .filter(
        (p) =>
          normalize([p.name, p.email, p.department].join(" ")).includes(q) &&
          !participants.some((s) => s.email.toLowerCase() === p.email?.toLowerCase()),
      )
      .slice(0, 6);
    matches.forEach((person) => {
      const button = node("button", (person.name || person.email) + " · " + person.email);
      button.type = "button";
      button.addEventListener("click", () => addPerson(person));
      $("participantSuggestions").append(button);
    });
    $("participantSuggestions").hidden = !matches.length;
  });
  $("addParticipant").addEventListener("click", () => {
    const value = $("participantSearch").value.trim(),
      match = catalog.people.find(
        (p) =>
          p.email?.toLowerCase() === value.toLowerCase() || normalize(p.name) === normalize(value),
      );
    addPerson(match || { email: value });
  });
  $("participantSearch").addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      $("addParticipant").click();
    }
  });
  function renderEquipment() {
    if (!catalog.equipment.length) return;
    $("equipmentOptions").replaceChildren();
    catalog.equipment.forEach((item) => {
      const id = String(item.id),
        label = node("label", null, "equipment-card"),
        input = node("input"),
        icon = node("span", "▣", "equipment-icon"),
        info = node("span"),
        name = node("strong", item.name),
        status = node(
          "small",
          item.available === true
            ? "Còn sẵn"
            : item.available === false
              ? "Không khả dụng"
              : "Chưa kiểm tra",
        );
      input.type = "checkbox";
      input.disabled = item.available !== true;
      input.checked = equipment.has(id) && !input.disabled;
      if (input.disabled) {
        equipment.delete(id);
        label.classList.add("disabled");
      }
      status.dataset.available = String(item.available);
      input.addEventListener("change", () => {
        input.checked ? equipment.add(id) : equipment.delete(id);
        dirty = true;
        preview();
      });
      info.append(name, status);
      label.append(input, icon, info);
      $("equipmentOptions").append(label);
    });
  }
  function reset() {
    availabilityKey = null;
    lookupVersion++;
    form.reset();
    participants = [];
    equipment.clear();
    dirty = false;
    renderChips();
    renderEquipment();
    preview();
    for (const id of [
      "meetingTitle",
      "startDate",
      "startTime",
      "endDate",
      "endTime",
      "meetingRoom",
    ])
      fieldError(id, "");
    $("participantError").textContent = "";
    $("participantSuggestions").hidden = true;
    $("roomCheckMessage").textContent = "Danh sách phòng sẽ được kết nối với API ở bước tiếp theo.";
    $("formStatus").hidden = true;
  }
  form.addEventListener("input", () => {
    dirty = true;
    preview();
  });
  form.addEventListener("change", () => {
    dirty = true;
    preview();
    if (room())
      $("roomCheckMessage").textContent =
        "Chưa kiểm tra lịch trống cho phòng và thời gian hiện tại.";
  });
  $("reset").addEventListener("click", () => {
    if (!dirty || confirm("Đặt lại toàn bộ thông tin đang nhập?")) reset();
  });
  $("cancel").addEventListener("click", () => {
    if (!dirty || confirm("Hủy các thông tin chưa lưu?")) {
      reset();
      message("Đã bỏ thông tin đang nhập. Chưa có lịch nào được tạo.");
    }
  });
  $("menuToggle").addEventListener("click", () =>
    document.body.classList.toggle("sidebar-collapsed"),
  );
  $("editPreview").addEventListener("click", () => $("meetingTitle").focus());
  window.MeetingFormUI = {
    setCatalog(data) {
      catalog = { ...catalog, ...data };
      $("meetingRoom").replaceChildren();
      const empty = node("option", "Chọn phòng họp");
      empty.value = "";
      $("meetingRoom").append(empty);
      catalog.rooms.forEach((r) => {
        const o = node("option", r.name + " · " + r.capacity + " người");
        o.value = String(r.id);
        o.disabled = r.available === false || r.status === "MAINTENANCE" || r.status === "INACTIVE";
        $("meetingRoom").append(o);
      });
      renderEquipment();
      preview();
    },
    getPayload() {
      return {
        title: $("meetingTitle").value.trim(),
        description: $("meetingDescription").value.trim(),
        startTime: $("startDate").value + "T" + $("startTime").value + ":00+07:00",
        endTime: $("endDate").value + "T" + $("endTime").value + ":00+07:00",
        room: $("meetingRoom").value,
        participants: participants.map((p) => p.email),
        equipmentIds: [...equipment],
        notes: $("meetingNotes").value.trim(),
      };
    },
    reset,
  };
  if (window.MeetingFormData) window.MeetingFormUI.setCatalog(window.MeetingFormData);

  let saving = false,
    availabilityKey = null,
    lookupVersion = 0;
  const formControls = () => [...form.querySelectorAll("input,select,textarea,button")];
  async function api(path, options = {}) {
    const controller = new AbortController(),
      timer = setTimeout(() => controller.abort(), 15000);
    try {
      const response = await fetch(path, {
        ...options,
        headers: {
          Accept: "application/json",
          ...(options.body ? { "Content-Type": "application/json" } : {}),
        },
        credentials: "same-origin",
        signal: controller.signal,
      });
      const text = await response.text();
      let body;
      try {
        body = text ? JSON.parse(text) : null;
      } catch (_) {
        throw Error("Máy chủ trả dữ liệu không hợp lệ.");
      }
      if (!response.ok)
        throw Error(
          body?.message ||
            body?.detail ||
            {
              401: "Vui lòng đăng nhập.",
              403: "Bạn không có quyền tạo lịch.",
              404: "Không tìm thấy dữ liệu hoặc API.",
              409: "Phòng, người tham dự hoặc thiết bị bị trùng lịch.",
            }[response.status] ||
            "API trả lỗi HTTP " + response.status,
        );
      return body;
    } catch (e) {
      if (e.name === "AbortError")
        throw Error("Máy chủ chưa phản hồi. Kiểm tra danh sách cuộc họp trước khi gửi lại.");
      if (e instanceof TypeError) throw Error("Không kết nối được backend cổng 8080.");
      throw e;
    } finally {
      clearTimeout(timer);
    }
  }
  function key() {
    const p = window.MeetingFormUI.getPayload();
    return [p.room, p.startTime, p.endTime].join("|");
  }
  async function checkAvailability() {
    if (!validate({ requireTitle: false })) return false;
    const requestKey = key(),
      token = ++lookupVersion,
      p = window.MeetingFormUI.getPayload();
    $("checkRoom").disabled = true;
    $("roomCheckMessage").textContent = "Đang kiểm tra phòng và thiết bị…";
    try {
      const params = new URLSearchParams({
          room: p.room,
          startTime: p.startTime,
          endTime: p.endTime,
        }),
        result = await api("/api/meetings/availability?" + params);
      if (token !== lookupVersion || requestKey !== key()) return false;
      const currentRoom = p.room;
      catalog.rooms = catalog.rooms.map((r) =>
        r.id === currentRoom
          ? {
              ...r,
              available: result.available,
              statusLabel: result.available ? "Còn trống" : "Không khả dụng",
              equipmentNames: result.equipment.map((e) => e.name),
            }
          : r,
      );
      catalog.equipment = result.equipment;
      equipment = new Set(
        [...equipment].filter((id) =>
          result.equipment.some((e) => String(e.id) === id && e.available),
        ),
      );
      renderEquipment();
      preview();
      availabilityKey = result.available ? requestKey : null;
      $("roomCheckMessage").textContent = result.available
        ? "Phòng còn trống. Thiết bị đã được kiểm tra cho toàn bộ khung giờ."
        : "Phòng đã được đặt hoặc không khả dụng.";
      return result.available;
    } catch (e) {
      availabilityKey = null;
      $("roomCheckMessage").textContent = e.message;
      return false;
    } finally {
      if (token === lookupVersion) $("checkRoom").disabled = false;
    }
  }
  ["startDate", "startTime", "endDate", "endTime", "meetingRoom"].forEach((id) =>
    $(id).addEventListener("change", () => {
      lookupVersion++;
      availabilityKey = null;
      equipment.clear();
      catalog.equipment = [];
      catalog.rooms = catalog.rooms.map((r) => ({
        ...r,
        available: undefined,
        statusLabel: undefined,
      }));
      $("equipmentOptions").replaceChildren(
        node("p", "Kiểm tra phòng để tải thiết bị khả dụng.", "field-note"),
      );
      $("roomCheckMessage").textContent =
        "Khung giờ hoặc phòng đã thay đổi. Kiểm tra lại trước khi tạo lịch.";
      $("checkRoom").disabled = false;
      preview();
    }),
  );
  $("checkRoom").addEventListener("click", checkAvailability);
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    if (saving) return;
    if (!validate()) {
      message("Kiểm tra thông tin được đánh dấu.", true);
      return;
    }
    saving = true;
    $("submit").disabled = true;
    try {
      const checked = availabilityKey === key() || (await checkAvailability());
      if (!checked) return;
      const p = window.MeetingFormUI.getPayload();
      const request = { ...p, organizerId: 1, recurrence: "NONE", repeatCount: 0 };
      formControls().forEach((el) => (el.disabled = true));
      message("Đang tạo lịch họp và đặt thiết bị…");
      const created = await api("/api/meetings", { method: "POST", body: JSON.stringify(request) });
      if (!created || created.id == null) throw Error("API không trả mã lịch vừa tạo.");
      dirty = false;
      message("Đã tạo lịch họp “" + created.title + "”. Mã cuộc họp: " + created.id + ".");
      availabilityKey = null;
    } catch (e) {
      message(e.message, true);
    } finally {
      saving = false;
      formControls().forEach((el) => (el.disabled = false));
      renderEquipment();
    }
  });
  async function loadCatalog() {
    $("submit").disabled = true;
    message("Đang tải danh sách phòng từ máy chủ…");
    try {
      const data = await api("/api/rooms");
      if (!Array.isArray(data)) throw Error("Danh sách phòng không hợp lệ.");
      window.MeetingFormUI.setCatalog({
        rooms: data.map((r) => ({
          id: r.roomId,
          name: r.roomName,
          capacity: r.capacity,
          status: r.status,
        })),
        includeOrganizer: true,
        organizer: { id: 1, name: "Người tổ chức" },
        equipment: [],
      });
      message(
        data.length
          ? "Đã tải danh sách phòng."
          : "Chưa có phòng trong cơ sở dữ liệu. Thêm phòng thật trong màn hình quản lý phòng trước.",
      );
      $("submit").disabled = !data.length;
    } catch (e) {
      message(e.message + " Tải lại trang để thử lại.", true);
    }
  }
  loadCatalog();
  preview();
})();
