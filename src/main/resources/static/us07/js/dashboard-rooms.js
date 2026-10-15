document.addEventListener("DOMContentLoaded", () => {
  const shared = window.RoomAvailability, $ = id => document.getElementById(id);
  let snapshot = null, sequence = 0, loading = false;
  const query = () => shared.range($("quickRoomDate").value, $("quickRoomStart").value, $("quickRoomEnd").value);
  const element = (tag, cls, text) => { const node = document.createElement(tag); node.className = cls; if (text !== undefined) node.textContent = text; return node; };
  function message(text, error = false) { $("quickRoomStatus").textContent = text; $("quickRoomStatus").dataset.error = String(error); }
  function render() {
    const list = $("quickRoomList"); list.replaceChildren();
    let count;
    try { count = shared.participantCount($("quickRoomCount").value, false); }
    catch (error) { message(error.message, true); return; }
    const current = query(), checked = snapshot && current?.key === snapshot.key;
    const rooms = shared.getRooms().filter(room => room.capacity >= count).sort((a, b) => a.capacity - b.capacity);
    if (!rooms.length) { list.append(element("p", "quick-room-empty", "Không có phòng đủ sức chứa. Hãy thay đổi số người.")); return; }
    rooms.forEach(room => {
      const busy = checked && snapshot.busyRoomNames.has(shared.nameKey(room.name));
      const row = element("div", "quick-room-row"), info = element("div", "quick-room-info");
      info.append(element("strong", "room-name", room.name), element("small", "room-meta", `${room.location} · ${room.capacity} người`), element("small", "room-meta", room.devices.join(", ") || "Chưa có thiết bị"));
      const actions = element("div", "quick-room-actions");
      actions.append(element("span", `state ${checked ? busy ? "busy" : "free" : "neutral"}`, checked ? busy ? "Đã được đặt" : "Còn trống" : loading ? "Đang kiểm tra" : "Chưa kiểm tra"));
      const button = element("button", "quick-room-select", "Chọn phòng"); button.type = "button"; button.disabled = !checked || busy || loading;
      button.addEventListener("click", () => {
        let people; try { people = shared.participantCount($("quickRoomCount").value, false); } catch (error) { message(error.message, true); return; }
        const latest = query(), selected = shared.getRooms().find(item => String(item.id) === String(room.id));
        if (!selected || !snapshot || snapshot.key !== latest?.key || snapshot.busyRoomNames.has(shared.nameKey(selected.name)) || selected.capacity < people) { snapshot = null; message("Phòng hoặc khung giờ đã thay đổi. Vui lòng kiểm tra lại.", true); render(); return; }
        window.location.href = shared.selectionUrl(selected, latest, people);
      });
      actions.append(button); row.append(info, actions); list.append(row);
    });
  }
  $("quickRoomForm").addEventListener("submit", async event => {
    event.preventDefault(); const request = ++sequence; snapshot = null;
    const current = query();
    try { shared.participantCount($("quickRoomCount").value, false); } catch (error) { message(error.message, true); render(); return; }
    if (!current) { message("Chọn ngày và giờ hợp lệ; giờ kết thúc phải sau giờ bắt đầu.", true); render(); return; }
    loading = true; $("quickRoomCheck").disabled = true; message("Đang kiểm tra lịch phòng…"); render();
    try {
      const result = await shared.check(current);
      if (request !== sequence || query()?.key !== current.key) return;
      snapshot = result;
      const count = shared.participantCount($("quickRoomCount").value, false);
      const suitable = shared.getRooms().filter(room => room.capacity >= count && !result.busyRoomNames.has(shared.nameKey(room.name))).length;
      message(suitable ? `Có ${suitable} phòng trống phù hợp. Chọn phòng để chuyển sang tạo lịch họp.` : "Không có phòng trống phù hợp. Hãy đổi khung giờ hoặc số người.", !suitable);
    } catch (error) { if (request === sequence) message(error.message, true); }
    finally { if (request === sequence) { loading = false; $("quickRoomCheck").disabled = false; render(); } }
  });
  ["quickRoomDate", "quickRoomStart", "quickRoomEnd", "quickRoomCount"].forEach(id => $(id).addEventListener("input", () => {
    ++sequence; snapshot = null; loading = false; $("quickRoomCheck").disabled = false; message("Thông tin đã thay đổi. Kiểm tra lại trước khi chọn phòng."); render();
  }));
  window.addEventListener("storage", event => { if (event.key === "meetingRooms" || event.key === shared.DEMO_KEY || event.key === null) { ++sequence; snapshot = null; loading = false; $("quickRoomCheck").disabled = false; message("Danh sách phòng đã thay đổi. Vui lòng kiểm tra lại."); render(); } });
  $("quickRoomDate").value = new Intl.DateTimeFormat("sv-SE", {timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit"}).format(new Date());
  document.querySelectorAll('.quick-room-all, .mini-card.green').forEach(link => link.addEventListener('click', () => {
    const params = new URLSearchParams();
    const selected = query();
    if (selected) { params.set('date', selected.date); params.set('startTime', selected.startTime); params.set('endTime', selected.endTime); }
    try { const count = shared.participantCount($('quickRoomCount').value, false); params.set('participantCount', count); } catch (_) {}
    link.href = 'rooms.html' + (params.size ? '?' + params : '');
  }));
  render();
});
