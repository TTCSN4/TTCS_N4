(function (root) {
  "use strict";
  const API = {
    availability: "/api/rooms/availability",
    equipment: "/api/equipment",
    meetings: "/api/meetings",
  };
  let cachedRooms = [];
  let cachedMeetings = [];
  let activeRequest = null;
  const nameKey = (name) => name.trim().toLocaleLowerCase("vi");
  function getRooms() {
    return cachedRooms.map((room) => ({
      ...room,
      devices: [...room.devices],
      deviceKeys: [...room.deviceKeys],
    }));
  }
  function getMeetings() {
    return cachedMeetings.map((meeting) => ({ ...meeting }));
  }
  function range(date, startTime, endTime) {
    if (
      !/^\d{4}-\d{2}-\d{2}$/.test(date) ||
      !/^([01]\d|2[0-3]):[0-5]\d$/.test(startTime) ||
      !/^([01]\d|2[0-3]):[0-5]\d$/.test(endTime)
    )
      return null;
    const calendar = new Date(`${date}T00:00:00Z`);
    if (
      !Number.isFinite(+calendar) ||
      calendar.toISOString().slice(0, 10) !== date
    )
      return null;
    const start = Date.parse(`${date}T${startTime}:00+07:00`),
      end = Date.parse(`${date}T${endTime}:00+07:00`);
    return end > start
      ? {
          date,
          startTime,
          endTime,
          start,
          end,
          key: `${date}|${startTime}|${endTime}`,
        }
      : null;
  }
  function participantCount(value, optional = true) {
    if (value === "" && optional) return 0;
    const count = Number(value);
    if (!Number.isInteger(count) || count < 1 || count > 200)
      throw Error("Số người phải là số nguyên từ 1 đến 200.");
    return count;
  }
  function busyRooms(meetings, query, excludeId = null) {
    if (!Array.isArray(meetings))
      throw Error(
        "Dữ liệu lịch họp không hợp lệ. Chưa thể xác nhận phòng trống.",
      );
    const busy = new Set();
    for (const meeting of meetings) {
      if (excludeId !== null && String(meeting?.id) === String(excludeId))
        continue;
      if (String(meeting?.status).toUpperCase() === "CANCELLED") continue;
      if (!meeting || typeof meeting.room !== "string" || !meeting.room.trim())
        throw Error(
          "Lịch họp thiếu thông tin phòng. Chưa thể xác nhận phòng trống.",
        );
      const start = Date.parse(meeting.startTime),
        end = Date.parse(meeting.endTime);
      if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start)
        throw Error(
          "Lịch họp có khung giờ không hợp lệ. Chưa thể xác nhận phòng trống.",
        );
      if (start < query.end && end > query.start)
        busy.add(nameKey(meeting.room));
    }
    return busy;
  }
  function roomActive(room) {
    return ["AVAILABLE", "ACTIVE"].includes(room.status || "AVAILABLE");
  }
  function currentRole() {
    return "Người dùng";
  }
  function canBook() {
    return true;
  }
  function roomReason(room) {
    return room.status === "MAINTENANCE"
      ? "Phòng đang bảo trì."
      : !roomActive(room)
        ? "Phòng ngừng sử dụng hoặc không hoạt động."
        : "";
  }
  const PARTICIPANTS_KEY = "meeting.us01.participants.v1";
  function participantSelection(value) {
    if (!value || !Array.isArray(value.participantIds))
      throw Error("Chưa nhận được danh sách người tham dự đã chọn từ US01.");
    const validId = (id) =>
      (typeof id === "string" && id.trim().length > 0 && id.length <= 128) ||
      (typeof id === "number" && Number.isSafeInteger(id) && id >= 0);
    if (value.participantIds.some((id) => !validId(id)))
      throw Error("Danh sách ID người tham dự từ US01 không hợp lệ.");
    if (value.organizerId != null && !validId(value.organizerId))
      throw Error("ID người chủ trì từ US01 không hợp lệ.");
    const participantIds = [
      ...new Set(value.participantIds.map((id) => String(id).trim())),
    ];
    const organizerId =
      value.organizerId == null ? null : String(value.organizerId).trim();
    if (
      value.organizerAttends != null &&
      typeof value.organizerAttends !== "boolean"
    )
      throw Error("Thông tin tham dự của chủ trì phải là true hoặc false.");
    const organizerAttends =
      value.organizerAttends == null
        ? organizerId !== null
        : value.organizerAttends;
    if (organizerAttends && !organizerId)
      throw Error("Chưa có ID của chủ trì tham dự. Vui lòng quay lại US01.");
    const all = new Set(participantIds);
    if (organizerAttends && organizerId) all.add(organizerId);
    if (!all.size)
      throw Error(
        "Danh sách từ US01 chưa có người tham dự hoặc chủ trì tham dự.",
      );
    if (all.size > 200)
      throw Error("Danh sách vượt giới hạn 200 người của hệ thống.");
    return {
      participantIds,
      organizerId,
      organizerAttends,
      participantCount: all.size,
    };
  }
  function errorMessage(response, payload) {
    return (
      payload?.detail ||
      payload?.message ||
      payload?.error ||
      `Máy chủ trả về lỗi HTTP ${response.status}.`
    );
  }
  async function requestJson(path, signal) {
    const response = await root.fetch(path, {
      method: "GET",
      headers: { Accept: "application/json" },
      credentials: "same-origin",
      signal,
    });
    const text = await response.text();
    let payload = null;
    if (text) {
      try {
        payload = JSON.parse(text);
      } catch {
        if (response.ok)
          throw Error("API trả về dữ liệu không phải JSON hợp lệ.");
      }
    }
    if (!response.ok) throw Error(errorMessage(response, payload));
    return payload;
  }
  function equipmentGroups(payload) {
    if (!Array.isArray(payload)) throw Error("Dữ liệu thiết bị không hợp lệ.");
    const groups = new Map();
    for (const item of payload) {
      if (!item || item.roomId == null || item.equipmentName == null) continue;
      const status = String(item.status || "AVAILABLE").toUpperCase();
      if (["INACTIVE", "DISABLED", "BROKEN", "MAINTENANCE"].includes(status))
        continue;
      const roomId = String(item.roomId);
      const current = groups.get(roomId) || [];
      current.push({
        name: String(item.equipmentName),
        type: String(item.type || item.equipmentName).toUpperCase(),
      });
      groups.set(roomId, current);
    }
    return groups;
  }
  function normalizeMeetings(payload) {
    if (!Array.isArray(payload)) throw Error("Dữ liệu lịch họp không hợp lệ.");
    return payload
      .filter((item) => item && (item.roomId != null || item.room != null))
      .map((item) => ({
        id: String(item.meetingId ?? item.id ?? ""),
        roomId: String(item.roomId ?? item.room),
        title: String(item.title || "Cuộc họp"),
        status: String(item.status || "SCHEDULED").toUpperCase(),
        startTime: String(item.startTime || ""),
        endTime: String(item.endTime || ""),
      }))
      .filter(
        (item) =>
          item.roomId &&
          Number.isFinite(Date.parse(item.startTime)) &&
          Number.isFinite(Date.parse(item.endTime)) &&
          Date.parse(item.endTime) > Date.parse(item.startTime),
      );
  }
  function deviceKey(value) {
    const key = String(value || "").toUpperCase();
    if (key.includes("PROJECTOR")) return "projector";
    if (key.includes("WIFI")) return "wifi";
    if (key.includes("BOARD")) return "board";
    if (key.includes("TV") || key.includes("DISPLAY")) return "tv";
    return key.toLocaleLowerCase("vi");
  }
  function normalizeRoom(item, equipment) {
    if (!item || item.roomId == null || item.roomName == null)
      throw Error("API phòng thiếu mã hoặc tên phòng.");
    const capacity = Number(item.capacity);
    if (!Number.isInteger(capacity) || capacity < 0)
      throw Error(`Sức chứa của phòng ${item.roomId} không hợp lệ.`);
    const roomId = String(item.roomId);
    const devices = equipment.get(roomId) || [];
    const availability = item.isAvailable ?? item.available;
    if (typeof availability !== "boolean")
      throw Error(`Trạng thái phòng ${roomId} không hợp lệ.`);
    return {
      id: roomId,
      name: String(item.roomName),
      capacity,
      location: String(item.location || "Chưa cập nhật vị trí"),
      status: String(item.status || "INACTIVE").toUpperCase(),
      isAvailable: availability,
      reason: item.reason == null ? "" : String(item.reason),
      devices: devices.map((device) => device.name),
      deviceKeys: [...new Set(devices.map((device) => deviceKey(device.type)))],
    };
  }
  async function check(query, minCapacity = null) {
    if (!query) throw Error("Khung giờ chưa hợp lệ.");
    const capacity =
      minCapacity == null ? null : participantCount(String(minCapacity), false);
    const parameters = new URLSearchParams({
      startTime: `${query.date}T${query.startTime}:00+07:00`,
      endTime: `${query.date}T${query.endTime}:00+07:00`,
    });
    if (capacity != null) parameters.set("minCapacity", String(capacity));
    if (activeRequest) activeRequest.abort();
    const controller = new AbortController();
    activeRequest = controller;
    let timedOut = false;
    const timeout = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, 10000);
    try {
      const results = await Promise.allSettled([
        requestJson(`${API.availability}?${parameters}`, controller.signal),
        requestJson(API.equipment, controller.signal),
        requestJson(API.meetings, controller.signal),
      ]);
      if (results[0].status === "rejected") throw results[0].reason;
      const summary = results[0].value;
      if (!summary || !Array.isArray(summary.rooms))
        throw Error("API trạng thái phòng trả về dữ liệu không hợp lệ.");
      const equipment =
        results[1].status === "fulfilled"
          ? equipmentGroups(results[1].value)
          : new Map();
      cachedRooms = summary.rooms.map((room) => normalizeRoom(room, equipment));
      cachedMeetings =
        results[2].status === "fulfilled"
          ? normalizeMeetings(results[2].value)
          : [];
      return {
        key: query.key,
        rooms: getRooms(),
        meetings: getMeetings(),
        totalRooms: Number(summary.totalRooms) || cachedRooms.length,
        availableRoomsCount:
          Number(summary.availableRoomsCount) ||
          cachedRooms.filter((room) => room.isAvailable).length,
        busyRoomsCount: Number(summary.busyRoomsCount) || 0,
        maintenanceRoomsCount: Number(summary.maintenanceRoomsCount) || 0,
        warnings: [
          results[1].status === "rejected"
            ? "Không tải được danh sách thiết bị."
            : "",
          results[2].status === "rejected"
            ? "Không tải được lịch sử dụng phòng."
            : "",
        ].filter(Boolean),
      };
    } catch (failure) {
      if (failure?.name === "AbortError")
        throw Error(
          timedOut
            ? "API phòng phản hồi quá chậm. Vui lòng thử lại."
            : "Yêu cầu kiểm tra trước đã được thay thế.",
        );
      throw Error(
        `Không thể tải trạng thái phòng từ hệ thống: ${failure.message}`,
      );
    } finally {
      clearTimeout(timeout);
      if (activeRequest === controller) activeRequest = null;
    }
  }
  function selectionUrl(room, query, count = 0) {
    const params = new URLSearchParams({
      roomId: room.id,
      date: query.date,
      startTime: query.startTime,
      endTime: query.endTime,
    });
    params.set("from", "US07");
    if (count) params.set("participantCount", count);
    return `create-meeting.html?${params}`;
  }
  const api = {
    PARTICIPANTS_KEY,
    participantSelection,
    roomActive,
    roomReason,
    currentRole,
    canBook,
    getRooms,
    getMeetings,
    range,
    nameKey,
    participantCount,
    busyRooms,
    check,
    selectionUrl,
  };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.RoomAvailability = api;
})(typeof window !== "undefined" ? window : globalThis);
