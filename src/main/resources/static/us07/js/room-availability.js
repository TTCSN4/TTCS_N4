(function (root) {
  "use strict";
  const defaults = [
    {id: 1, name: "Phòng họp A1", location: "Tầng 2, khu A", capacity: 8, devices: ["Máy chiếu", "TV", "Bảng trắng"]},
    {id: 2, name: "Phòng họp B2", location: "Tầng 3, khu B", capacity: 12, devices: ["Máy chiếu", "Micro", "Camera", "Wi‑Fi"]},
    {id: 3, name: "Phòng họp C1", location: "Tầng 1, khu C", capacity: 6, devices: ["TV", "Bảng trắng"]},
    {id: 4, name: "Phòng họp Ocean", location: "Tầng 3, khu A", capacity: 20, devices: ["Máy chiếu", "Camera", "Wi-Fi"]},
    {id: 5, name: "Phòng họp Sakura", location: "Tầng 2, khu B", capacity: 16, devices: ["Máy chiếu", "Bảng trắng", "Wi-Fi"]},
    {id: 6, name: "Phòng hội nghị", location: "Tầng 1, khu A", capacity: 30, devices: ["Máy chiếu", "TV", "Wi-Fi"]}
  ];
  const nameKey = name => name.trim().toLocaleLowerCase("vi");
  function getRooms(storage = root.localStorage) {
    let rooms = defaults;
    try { const raw = storage.getItem("meetingRooms"); if (raw !== null) { const saved = JSON.parse(raw); if (!Array.isArray(saved)) throw Error("Invalid rooms"); rooms = saved; } }
    catch (_) { rooms = defaults; }
    return rooms.filter(room => room && typeof room.name === "string" && room.name.trim() && room.id != null).map(room => ({...room, capacity: Number(room.capacity) || 0, location: String(room.location || ""), devices: Array.isArray(room.devices) ? room.devices.filter(device => typeof device === "string") : []}));
  }
  function range(date, startTime, endTime) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !/^([01]\d|2[0-3]):[0-5]\d$/.test(startTime) || !/^([01]\d|2[0-3]):[0-5]\d$/.test(endTime)) return null;
    const calendar = new Date(`${date}T00:00:00Z`);
    if (!Number.isFinite(+calendar) || calendar.toISOString().slice(0, 10) !== date) return null;
    const start = Date.parse(`${date}T${startTime}:00+07:00`), end = Date.parse(`${date}T${endTime}:00+07:00`);
    return end > start ? {date, startTime, endTime, start, end, key: `${date}|${startTime}|${endTime}`} : null;
  }
  function participantCount(value, optional = true) { if (value === "" && optional) return 0; const count = Number(value); if (!Number.isInteger(count) || count < 1 || count > 200) throw Error("Số người phải là số nguyên từ 1 đến 200."); return count; }
  function busyRooms(meetings, query, excludeId = null) {
    if (!Array.isArray(meetings)) throw Error("Dữ liệu lịch họp không hợp lệ. Chưa thể xác nhận phòng trống.");
    const busy = new Set();
    for (const meeting of meetings) {
      if (excludeId !== null && String(meeting?.id) === String(excludeId)) continue;
      if (String(meeting?.status).toUpperCase() === "CANCELLED") continue;
      if (!meeting || typeof meeting.room !== "string" || !meeting.room.trim()) throw Error("Lịch họp thiếu thông tin phòng. Chưa thể xác nhận phòng trống.");
      const start = Date.parse(meeting.startTime), end = Date.parse(meeting.endTime);
      if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) throw Error("Lịch họp có khung giờ không hợp lệ. Chưa thể xác nhận phòng trống.");
      if (start < query.end && end > query.start) busy.add(nameKey(meeting.room));
    }
    return busy;
  }
  const DEMO_KEY = "meetingRoomBookings.demo.v1";
  function demoMeetings(day, storage = root.localStorage) {
    const raw = storage.getItem(DEMO_KEY);
    const saved = raw === null ? [] : JSON.parse(raw);
    if (!Array.isArray(saved)) throw Error("Dữ liệu lịch demo bị lỗi. Hãy xóa mục meetingRoomBookings.demo.v1 trong bộ nhớ trình duyệt rồi thử lại.");
    // These labelled demo bookings repeat on the selected date for a predictable demo.
    const samples = [
      {id: "demo-a1", room: "Phòng họp A1", status: "SCHEDULED", startTime: `${day}T09:00:00+07:00`, endTime: `${day}T10:00:00+07:00`},
      {id: "demo-b2", room: "Phòng họp B2", status: "SCHEDULED", startTime: `${day}T14:00:00+07:00`, endTime: `${day}T15:00:00+07:00`}
    ];
    return [...samples, ...saved];
  }
  async function check(query, excludeId = null) {
    if (!query) throw Error("Khung giờ chưa hợp lệ.");
    await new Promise(resolve => setTimeout(resolve, 180));
    try { return {key: query.key, busyRoomNames: busyRooms(demoMeetings(query.date), query, excludeId)}; }
    catch (error) { throw Error(`Không thể kiểm tra phòng trong dữ liệu demo: ${error.message}`); }
  }
  async function createDemo(payload) {
    const clock = value => new Intl.DateTimeFormat("en-GB", {timeZone: "Asia/Ho_Chi_Minh", hour: "2-digit", minute: "2-digit", hourCycle: "h23"}).format(new Date(value));
    const day = new Intl.DateTimeFormat("sv-SE", {timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit"}).format(new Date(payload.startTime));
    const query = range(day, clock(payload.startTime), clock(payload.endTime));
    const room = getRooms().find(room => nameKey(room.name) === nameKey(payload.room));
    const count = participantCount(String(payload.participantCount), false);
    if (!query || query.start <= Date.now()) throw Error("Ngày giờ họp phải hợp lệ và nằm trong tương lai.");
    if (!room || room.capacity < count) throw Error("Phòng đã chọn không đủ sức chứa hoặc không còn tồn tại.");
    const result = await check(query);
    if (result.busyRoomNames.has(nameKey(room.name))) throw Error("Phòng đã được đặt trong khung giờ này (dữ liệu mô phỏng). Hãy chọn phòng khác.");
    const raw = root.localStorage.getItem(DEMO_KEY), saved = raw === null ? [] : JSON.parse(raw);
    if (!Array.isArray(saved)) throw Error("Dữ liệu lịch demo không hợp lệ.");
    const created = {...payload, id: `DEMO-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`};
    root.localStorage.setItem(DEMO_KEY, JSON.stringify([...saved, created]));
    root.dispatchEvent?.(new root.Event("room-demo-updated"));
    return created;
  }
  function selectionUrl(room, query, count = 0) {
    const params = new URLSearchParams({roomId: room.id, date: query.date, startTime: query.startTime, endTime: query.endTime});
    if (count) params.set("participantCount", count);
    return `create-meeting.html?${params}`;
  }
  const api = {defaults, getRooms, range, nameKey, participantCount, busyRooms, check, selectionUrl, demoMeetings, createDemo, DEMO_KEY, demo: true};
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.RoomAvailability = api;
})(typeof window !== "undefined" ? window : globalThis);
