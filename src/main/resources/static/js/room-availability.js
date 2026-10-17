(function (root) {
  "use strict";
  const defaults = [
    {
      id: 1,
      name: "Phòng A101",
      location: "Tòa A · Tầng 1",
      capacity: 10,
      status: "AVAILABLE",
      devices: ["Máy chiếu", "Màn hình", "Micro"],
    },
    {
      id: 2,
      name: "Phòng A102",
      location: "Tòa A · Tầng 1",
      capacity: 20,
      status: "AVAILABLE",
      devices: ["Máy chiếu", "Màn hình", "Camera"],
    },
    {
      id: 3,
      name: "Phòng B201",
      location: "Tòa B · Tầng 2",
      capacity: 15,
      status: "AVAILABLE",
      devices: ["Màn hình", "Micro"],
    },
    {
      id: 4,
      name: "Phòng B202",
      location: "Tòa B · Tầng 2",
      capacity: 30,
      status: "AVAILABLE",
      devices: ["Máy chiếu", "Màn hình", "Micro", "Camera"],
    },
    {
      id: 5,
      name: "Phòng C301",
      location: "Tòa C · Tầng 3",
      capacity: 12,
      status: "MAINTENANCE",
      devices: ["Màn hình", "Micro"],
    },
    {
      id: 6,
      name: "Phòng C302",
      location: "Tòa C · Tầng 3",
      capacity: 25,
      status: "AVAILABLE",
      devices: ["Máy chiếu", "Màn hình", "Camera"],
    },
    {
      id: 7,
      name: "Phòng D401",
      location: "Tòa D · Tầng 4",
      capacity: 8,
      status: "INACTIVE",
      devices: ["Màn hình"],
    },
    {
      id: 8,
      name: "Phòng D402",
      location: "Tòa D · Tầng 4",
      capacity: 6,
      status: "AVAILABLE",
      devices: ["Màn hình", "Bảng trắng"],
    },
  ];
  const nameKey = (name) => name.trim().toLocaleLowerCase("vi");
  function getRooms(storage = root.localStorage) {
    let rooms = defaults;
    try {
      const raw = storage.getItem("meetingRooms");
      if (raw !== null) {
        const saved = JSON.parse(raw);
        if (!Array.isArray(saved)) throw Error("Invalid rooms");
        rooms = saved;
      }
    } catch (_) {
      rooms = defaults;
    }
    return rooms
      .filter(
        (room) =>
          room &&
          typeof room.name === "string" &&
          room.name.trim() &&
          room.id != null,
      )
      .map((room) => ({
        ...room,
        status: String(room.status || "AVAILABLE").toUpperCase(),
        capacity: Number(room.capacity) || 0,
        location: String(room.location || ""),
        devices: Array.isArray(room.devices)
          ? room.devices.filter((device) => typeof device === "string")
          : [],
      }));
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
    return (
      new URLSearchParams(root.location?.search || "")
        .get("role")
        ?.toUpperCase() || "PM"
    );
  }
  function canBook() {
    return ["ADMIN", "PM"].includes(currentRole());
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
    if (
      !value.participantIds.length ||
      value.participantIds.some((id) => !validId(id))
    )
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
    if (all.size > 200)
      throw Error("Danh sách vượt giới hạn 200 người của bản mô phỏng.");
    return {
      participantIds,
      organizerId,
      organizerAttends,
      participantCount: all.size,
    };
  }
  const DEMO_KEY = "meetingRoomBookings.demo.v1";
  function demoMeetings(day, storage = root.localStorage) {
    const raw = storage.getItem(DEMO_KEY);
    const saved = raw === null ? [] : JSON.parse(raw);
    if (!Array.isArray(saved))
      throw Error(
        "Dữ liệu lịch demo bị lỗi. Hãy xóa mục meetingRoomBookings.demo.v1 trong bộ nhớ trình duyệt rồi thử lại.",
      );
    // These labelled demo bookings repeat on the selected date for a predictable demo.
    const samples = [
      {
        id: "demo-a101-morning",
        room: "Phòng A101",
        status: "SCHEDULED",
        startTime: `${day}T08:00:00+07:00`,
        endTime: `${day}T09:00:00+07:00`,
      },
      {
        id: "demo-a101-noon",
        room: "Phòng A101",
        status: "SCHEDULED",
        startTime: `${day}T11:00:00+07:00`,
        endTime: `${day}T11:30:00+07:00`,
      },
      {
        id: "demo-a102",
        room: "Phòng A102",
        status: "SCHEDULED",
        startTime: `${day}T09:00:00+07:00`,
        endTime: `${day}T10:30:00+07:00`,
      },
      {
        id: "demo-c302",
        room: "Phòng C302",
        status: "SCHEDULED",
        startTime: `${day}T08:00:00+07:00`,
        endTime: `${day}T12:00:00+07:00`,
      },
    ];
    return [...samples, ...saved];
  }
  async function check(query, excludeId = null) {
    if (!query) throw Error("Khung giờ chưa hợp lệ.");
    await new Promise((resolve) => setTimeout(resolve, 180));
    try {
      return {
        key: query.key,
        busyRoomNames: busyRooms(demoMeetings(query.date), query, excludeId),
      };
    } catch (error) {
      throw Error(
        `Không thể kiểm tra phòng trong dữ liệu demo: ${error.message}`,
      );
    }
  }
  async function createDemo(payload) {
    if (!canBook())
      throw Error("Chỉ Admin và PM được đặt phòng trong bản mô phỏng.");
    const clock = (value) =>
      new Intl.DateTimeFormat("en-GB", {
        timeZone: "Asia/Ho_Chi_Minh",
        hour: "2-digit",
        minute: "2-digit",
        hourCycle: "h23",
      }).format(new Date(value));
    const day = new Intl.DateTimeFormat("sv-SE", {
      timeZone: "Asia/Ho_Chi_Minh",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).format(new Date(payload.startTime));
    const query = range(day, clock(payload.startTime), clock(payload.endTime));
    const room = getRooms().find(
      (room) => nameKey(room.name) === nameKey(payload.room),
    );
    const count = participantCount(String(payload.participantCount), false);
    if (!query || query.start <= Date.now())
      throw Error("Ngày giờ họp phải hợp lệ và nằm trong tương lai.");
    if (!room || !roomActive(room) || room.capacity < count)
      throw Error("Phòng đã chọn không đủ sức chứa hoặc không còn tồn tại.");
    const result = await check(query);
    if (result.busyRoomNames.has(nameKey(room.name)))
      throw Error(
        "Phòng đã được đặt trong khung giờ này (dữ liệu mô phỏng). Hãy chọn phòng khác.",
      );
    const raw = root.localStorage.getItem(DEMO_KEY),
      saved = raw === null ? [] : JSON.parse(raw);
    if (!Array.isArray(saved)) throw Error("Dữ liệu lịch demo không hợp lệ.");
    const created = {
      ...payload,
      id: `DEMO-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    };
    root.localStorage.setItem(DEMO_KEY, JSON.stringify([...saved, created]));
    root.dispatchEvent?.(new root.Event("room-demo-updated"));
    return created;
  }
  function selectionUrl(room, query, count = 0) {
    const params = new URLSearchParams({
      roomId: room.id,
      date: query.date,
      startTime: query.startTime,
      endTime: query.endTime,
    });
    params.set("role", currentRole());
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
    defaults,
    getRooms,
    range,
    nameKey,
    participantCount,
    busyRooms,
    check,
    selectionUrl,
    demoMeetings,
    createDemo,
    DEMO_KEY,
    demo: true,
  };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.RoomAvailability = api;
})(typeof window !== "undefined" ? window : globalThis);
