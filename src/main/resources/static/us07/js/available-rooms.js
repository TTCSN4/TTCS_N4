(() => {
  "use strict";
  const $ = id => document.getElementById(id);
  const shared = window.RoomAvailability;
  let rooms = [], snapshot = null, generation = 0, loading = false, selecting = false, selectedId = null, toastTimer;
  const normalize = value => String(value || "").toLocaleLowerCase("vi").normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/đ/g, "d");
  const deviceKey = value => {
    const key = normalize(value).replace(/[^a-z0-9]/g, "");
    return ({maychieu:"projector",wifi:"wifi",bangtrang:"board",bangviet:"board",tv:"tv"})[key] || key;
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
    use.setAttribute("href", "#i-" + name); svg.append(use); return svg;
  }
  const currentRange = () => shared.range($("date").value, $("start-time").value, $("end-time").value);
  const currentSnapshot = () => snapshot && snapshot.key === currentRange()?.key;
  const free = room => Boolean(currentSnapshot() && !snapshot.busyRoomNames.has(shared.nameKey(room.name)));
  const people = () => shared.participantCount($("people").value, false);
  const labelDate = value => new Intl.DateTimeFormat("vi-VN", {timeZone:"Asia/Ho_Chi_Minh", day:"2-digit", month:"2-digit", year:"numeric"}).format(new Date(value + "T12:00:00+07:00"));
  const rangeLabel = range => `${labelDate(range.date)} · ${range.startTime} – ${range.endTime}`;
  function toast(message) {
    $("toast").textContent = message; $("toast").hidden = false;
    clearTimeout(toastTimer); toastTimer = setTimeout(() => $("toast").hidden = true, 4200);
  }
  function error(message, field) {
    $("form-error").textContent = message; $("form-error").hidden = !message;
    for (const id of ["date", "start-time", "end-time", "people"]) $(id).setAttribute("aria-invalid", String(id === field));
  }
  function loadRooms() {
    rooms = shared.getRooms().map(room => ({...room, id:String(room.id)}));
    const previous = $("location").value;
    $("location").replaceChildren(new Option("Tất cả vị trí", ""));
    [...new Set(rooms.map(room => room.location).filter(Boolean))].sort((a,b) => a.localeCompare(b,"vi")).forEach(location => $("location").append(new Option(location, location)));
    if ([...$("location").options].some(option => option.value === previous)) $("location").value = previous;
  }
  function devices(room) {
    const list = node("div", undefined, "device-list");
    if (!room.devices.length) { list.append(node("span", "Chưa cập nhật thiết bị", "device-tag")); return list; }
    for (const device of room.devices) {
      const tag = node("span", undefined, "device-tag"), key = deviceKey(device);
      if (key === "wifi" || key === "projector") tag.append(icon(key === "wifi" ? "wifi" : "projector"));
      tag.append(document.createTextNode(device)); list.append(tag);
    }
    return list;
  }
  function badge(room) {
    const checked = currentSnapshot();
    return node("span", checked ? free(room) ? "Còn trống" : "Đã được đặt" : "Chưa kiểm tra", `status-badge${checked ? free(room) ? "" : " busy" : " unknown"}`);
  }
  function button(text, className, iconName, listener) {
    const element = node("button", undefined, className); element.type = "button";
    element.append(node("span", text)); if (iconName) element.append(icon(iconName));
    element.addEventListener("click", listener); return element;
  }
  function card(room) {
    const element = node("article", undefined, "room-card"); element.dataset.roomId = room.id;
    const header = node("div", undefined, "room-card-head"), mark = node("span", undefined, "room-mark"), identity = node("div", undefined, "room-identity");
    mark.append(icon("rooms")); identity.append(node("h3", room.name));
    const location = node("div", undefined, "room-location"); location.append(icon("pin"), document.createTextNode(room.location || "Chưa cập nhật vị trí")); identity.append(location);
    header.append(mark, identity, badge(room));
    const meta = node("div", undefined, "room-meta-row"), capacity = node("span", undefined, "capacity");
    capacity.append(icon("users"), node("strong", String(room.capacity)), document.createTextNode("người"));
    meta.append(capacity, node("span", "PHÒNG " + room.id, "room-id"));
    const footer = node("div", undefined, "room-card-footer");
    footer.append(button("Xem chi tiết", "details-button", "arrow", () => openDetail(room.id)));
    const select = button(free(room) ? "Chọn phòng" : currentSnapshot() ? "Đã được đặt" : "Kiểm tra phòng", "primary-button", free(room) ? "arrow" : undefined, () => selectRoom(room.id));
    select.dataset.selectRoom = room.id; select.disabled = !free(room) || loading || selecting;
    footer.append(select); element.append(header, meta, devices(room), footer); return element;
  }
  function stats(count) {
    $("stat-total").textContent = rooms.length;
    $("stat-available").textContent = currentSnapshot() ? rooms.filter(free).length : "—";
    $("stat-busy").textContent = currentSnapshot() ? rooms.filter(room => !free(room)).length : "—";
    $("stat-suitable").textContent = currentSnapshot() && count ? rooms.filter(room => free(room) && room.capacity >= count).length : "—";
  }
  function render() {
    let count;
    try { count = people(); } catch { count = null; }
    stats(count);
    $("room-list").setAttribute("aria-busy", String(loading));
    $("find-rooms").disabled = loading;
    $("find-rooms").querySelector("span").textContent = loading ? "Đang kiểm tra…" : "Tìm phòng trống";
    $("room-list").replaceChildren();
    if (loading) {
      for (let i=0;i<4;i++) { const placeholder = node("div", undefined, "loading-card"); placeholder.setAttribute("aria-hidden","true"); $("room-list").append(placeholder); }
      $("empty-state").hidden = true; $("result-count").textContent = "…"; $("query-summary").textContent = "Đang kiểm tra lịch phòng…"; return;
    }
    const query = normalize($("search").value.trim()), location = $("location").value, state = $("availability-filter").value;
    const requested = [...document.querySelectorAll('[name="device"]:checked')].map(input => input.value);
    const filtered = count ? rooms.filter(room => {
      const fits = room.capacity >= count && (!location || room.location === location) && normalize(room.name + " " + room.location).includes(query);
      const equipment = requested.every(key => room.devices.some(device => deviceKey(device) === key));
      const status = !currentSnapshot() || state === "all" || (state === "available" ? free(room) : !free(room));
      return fits && equipment && status;
    }) : [];
    const sort = $("sort").value;
    filtered.sort((a,b) => sort === "name" ? a.name.localeCompare(b.name,"vi") : (sort === "capacity-desc" ? b.capacity-a.capacity : a.capacity-b.capacity) || a.name.localeCompare(b.name,"vi"));
    $("room-list").append(...filtered.map(card));
    $("result-count").textContent = filtered.length;
    $("query-summary").textContent = currentSnapshot() ? `${rangeLabel(currentRange())} · ${count || "—"} người tham dự` : "Chọn khung giờ và nhấn Tìm phòng trống để cập nhật tình trạng phòng.";
    $("empty-state").hidden = filtered.length > 0;
    $("empty-message").textContent = !count ? "Nhập số người tham dự hợp lệ để tìm phòng." : !rooms.length ? "Danh sách phòng hiện chưa có dữ liệu." : "Thử thay đổi thời gian, số người hoặc điều kiện tìm kiếm.";
    if ($("room-detail").open) renderDetail();
  }
  async function check() {
    const request = ++generation; snapshot = null; error("");
    const range = currentRange();
    if (!range) { error("Chọn ngày hợp lệ và giờ kết thúc sau giờ bắt đầu.", $("date").value ? "end-time" : "date"); loading = false; render(); return; }
    try { people(); } catch (failure) { error(failure.message, "people"); loading = false; render(); return; }
    loading = true; render();
    try {
      const result = await shared.check(range);
      if (request !== generation || currentRange()?.key !== range.key) return;
      loadRooms(); snapshot = result;
    } catch (failure) { if (request === generation) error(failure.message); }
    finally { if (request === generation) { loading = false; render(); } }
  }
  function renderDetail() {
    const room = rooms.find(room => room.id === selectedId); if (!room) { $("room-detail").close(); return; }
    $("detail-title").textContent = room.name; $("detail-location").textContent = room.location || "Chưa cập nhật vị trí";
    $("detail-status").replaceWith(Object.assign(badge(room), {id:"detail-status"}));
    $("detail-capacity").textContent = `Sức chứa tối đa ${room.capacity} người`;
    $("detail-devices").replaceChildren(...devices(room).childNodes);
    const range = currentRange(); $("detail-range").textContent = range ? rangeLabel(range) : "Chưa có khung giờ hợp lệ";
    $("detail-schedule").replaceChildren();
    let bookings = [];
    try { if (range) bookings = shared.demoMeetings(range.date).filter(meeting => shared.nameKey(meeting.room) === shared.nameKey(room.name) && meeting.status !== "CANCELLED" && new Intl.DateTimeFormat("sv-SE",{timeZone:"Asia/Ho_Chi_Minh",year:"numeric",month:"2-digit",day:"2-digit"}).format(new Date(meeting.startTime)) === range.date); } catch { $("detail-schedule").append(node("p", "Chưa đọc được lịch phòng.", "schedule-empty")); }
    const clock = value => new Intl.DateTimeFormat("en-GB",{timeZone:"Asia/Ho_Chi_Minh",hour:"2-digit",minute:"2-digit",hourCycle:"h23"}).format(new Date(value));
    bookings.sort((a,b) => Date.parse(a.startTime)-Date.parse(b.startTime));
    for (const booking of bookings) { const row = node("div", undefined, "schedule-item"); row.append(node("strong", `${clock(booking.startTime)} – ${clock(booking.endTime)}`), node("span", "Đã được đặt")); $("detail-schedule").append(row); }
    if (!bookings.length && !$("detail-schedule").childNodes.length) $("detail-schedule").append(node("p", "Chưa có lịch đặt trong ngày này.", "schedule-empty"));
    let count = 0; try { count = people(); } catch {}
    const past = range && range.start <= Date.now();
    const allowed = free(room) && !past && room.capacity >= count && count > 0;
    $("select-detail").disabled = !allowed || selecting || loading;
    $("detail-message").textContent = !currentSnapshot() ? "Kiểm tra lại khung giờ trước khi chọn phòng." : !free(room) ? "Phòng đã có lịch trong khung giờ này. Bạn có thể thay đổi thời gian tìm kiếm." : past ? "Chọn khung giờ trong tương lai để tạo lịch họp." : room.capacity < count ? "Phòng chưa đủ sức chứa cho số người đã chọn." : "";
  }
  function openDetail(id) {
    selectedId = id; renderDetail(); $("room-detail").showModal();
  }
  async function selectRoom(id) {
    if (selecting || loading) return;
    const range = currentRange(), room = rooms.find(room => room.id === id);
    let count; try { count = people(); } catch (failure) { error(failure.message, "people"); return; }
    if (!range || !room || !free(room) || room.capacity < count) { toast("Thông tin đã thay đổi. Vui lòng tìm phòng lại."); return; }
    if (range.start <= Date.now()) { toast("Chọn khung giờ trong tương lai để tạo lịch họp."); return; }
    const request = generation; selecting = true; render();
    try {
      const result = await shared.check(range), latest = shared.getRooms().find(item => String(item.id) === id);
      if (request !== generation || currentRange()?.key !== range.key) return;
      snapshot = result;
      if (!latest || latest.capacity < people() || result.busyRoomNames.has(shared.nameKey(room.name))) { toast("Phòng vừa thay đổi hoặc đã được đặt. Vui lòng chọn phòng khác."); loadRooms(); return; }
      const draft = {roomId:id, roomName:room.name, date:range.date, startTime:range.startTime, endTime:range.endTime, participantCount:people()};
      try { sessionStorage.setItem("meeting.us07.selection", JSON.stringify(draft)); } catch {}
      window.location.assign(shared.selectionUrl(latest, range, people()));
    } catch (failure) { toast(failure.message); }
    finally { selecting = false; render(); }
  }
  function clearSecondary() {
    $("search").value = ""; $("location").value = ""; $("availability-filter").value = "available"; $("sort").value = "capacity";
    document.querySelectorAll('[name="device"]').forEach(input => input.checked = false); render();
  }
  function defaults() {
    const now = new Date(Date.now() + 30*60000);
    const parts = Object.fromEntries(new Intl.DateTimeFormat("sv-SE",{timeZone:"Asia/Ho_Chi_Minh",year:"numeric",month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit",hourCycle:"h23"}).formatToParts(now).filter(part => part.type !== "literal").map(part => [part.type,part.value]));
    let date = `${parts.year}-${parts.month}-${parts.day}`, minute = Number(parts.hour)*60 + Math.ceil(Number(parts.minute)/30)*30;
    if (minute < 9*60) minute = 9*60;
    if (minute > 17*60) { date = new Date(Date.parse(date+"T12:00:00+07:00") + 86400000).toISOString().slice(0,10); minute = 9*60; }
    const clock = value => String(Math.floor(value/60)).padStart(2,"0") + ":" + String(value%60).padStart(2,"0");
    const params = new URLSearchParams(location.search);
    $("date").value = params.get("date") || date; $("start-time").value = params.get("startTime") || clock(minute); $("end-time").value = params.get("endTime") || clock(minute+60); $("people").value = params.get("participantCount") || "6";
  }
  $("availability-form").addEventListener("submit", event => { event.preventDefault(); check(); });
  for (const id of ["date", "start-time", "end-time"]) $(id).addEventListener("input", () => { ++generation; snapshot = null; loading = false; error(""); render(); });
  $("people").addEventListener("input", () => { error(""); render(); });
  for (const id of ["search", "location", "sort", "availability-filter"]) $(id).addEventListener(id === "search" ? "input" : "change", render);
  document.querySelectorAll('[name="device"]').forEach(input => input.addEventListener("change", render));
  $("reset-filters").addEventListener("click", () => { $("people").value = "6"; clearSecondary(); error(""); });
  $("empty-reset").addEventListener("click", clearSecondary);
  for (const [id,list] of [["view-grid",false],["view-list",true]]) $(id).addEventListener("click", () => { $("room-list").classList.toggle("list-view",list); $("view-grid").setAttribute("aria-pressed",String(!list)); $("view-list").setAttribute("aria-pressed",String(list)); });
  $("select-detail").addEventListener("click", () => selectRoom(selectedId));
  for (const id of ["close-detail","cancel-detail"]) $(id).addEventListener("click", () => $("room-detail").close());
  $("help").addEventListener("click", () => $("help-dialog").showModal()); $("close-help").addEventListener("click", () => $("help-dialog").close());
  $("notifications").addEventListener("click", () => toast("Bạn chưa có thông báo mới trong bản minh họa."));
  function toggleMenu() {
    const mobile = matchMedia("(max-width: 800px)").matches;
    document.body.classList.toggle(mobile ? "menu-open" : "menu-closed");
    const open = mobile ? document.body.classList.contains("menu-open") : !document.body.classList.contains("menu-closed");
    $("menu-toggle").setAttribute("aria-expanded",String(open)); $("menu-overlay").hidden = !open || !mobile;
  }
  $("menu-toggle").addEventListener("click", toggleMenu); $("menu-overlay").addEventListener("click", toggleMenu);
  $("menu-toggle").setAttribute("aria-expanded",String(!matchMedia("(max-width: 800px)").matches));
  window.addEventListener("storage", event => { if (["meetingRooms",shared.DEMO_KEY,null].includes(event.key)) check(); });
  window.addEventListener("room-demo-updated", check);
  loadRooms(); defaults(); check();
})();
