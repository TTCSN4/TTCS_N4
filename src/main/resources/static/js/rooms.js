const ROOM_STORAGE_KEY = "meetingRooms";

const defaultRooms = window.RoomAvailability.defaults;

const form = document.getElementById("roomForm");
const formTitle = document.getElementById("formTitle");
const roomIdInput = document.getElementById("roomId");
const roomNameInput = document.getElementById("roomName");
const roomLocationInput = document.getElementById("roomLocation");
const roomCapacityInput = document.getElementById("roomCapacity");
const roomDeviceCheckboxes = Array.from(document.querySelectorAll('#roomDevices input[type="checkbox"]'));
const roomError = document.getElementById("roomError");
const roomSuccess = document.getElementById("roomSuccess");
const roomTableBody = document.getElementById("roomTableBody");
const roomCount = document.getElementById("roomCount");
const resetButton = document.getElementById("resetRoomForm");
const roomDetailsDialog = document.getElementById("roomDetailsDialog");
const roomDetailsName = document.getElementById("roomDetailsName");
const roomDetailsLocation = document.getElementById("roomDetailsLocation");
const roomDetailsCapacity = document.getElementById("roomDetailsCapacity");
const roomDetailsDevices = document.getElementById("roomDetailsDevices");
const roomDetailsAvailability = document.getElementById("roomDetailsAvailability");
const availabilityDate = document.getElementById("availabilityDate");
const availabilityStart = document.getElementById("availabilityStart");
const availabilityEnd = document.getElementById("availabilityEnd");
const availabilityMessage = document.getElementById("availabilityMessage");
const checkAvailabilityButton = document.getElementById("checkRoomAvailability");
let availabilitySnapshot = null;
let availabilitySequence = 0;

function escapeHtml(value) {
    const element = document.createElement("span");
    element.textContent = value ?? "";
    return element.innerHTML;
}

function getAvailabilityQuery() { return window.RoomAvailability.range(availabilityDate.value, availabilityStart.value, availabilityEnd.value); }

function getRoomAvailability(room) {
    const query = getAvailabilityQuery();
    if (!availabilitySnapshot) return { label: "Chưa kiểm tra", state: "neutral" };
    if (!query || query.key !== availabilitySnapshot.key) {
        return { label: "Cần kiểm tra lại", state: "neutral" };
    }

    const isBusy = availabilitySnapshot.busyRoomNames.has(room.name.trim().toLocaleLowerCase("vi"));
    return isBusy
        ? { label: "Đã được đặt", state: "busy" }
        : { label: "Còn trống", state: "free" };
}

async function checkRoomAvailability() {
    const request = ++availabilitySequence;
    const query = getAvailabilityQuery();
    if (!query) {
        availabilitySnapshot = null;
        availabilityMessage.textContent = "Vui lòng chọn ngày và khoảng giờ hợp lệ (giờ kết thúc phải sau giờ bắt đầu).";
        availabilityMessage.className = "availability-message is-error";
        renderRoomTable();
        return;
    }

    availabilitySnapshot = null;
    renderRoomTable();
    checkAvailabilityButton.disabled = true;
    availabilityMessage.textContent = "Đang kiểm tra lịch phòng…";
    availabilityMessage.className = "availability-message";
    try {
        const {busyRoomNames} = await window.RoomAvailability.check(query);
        if (request !== availabilitySequence) return;
        if (getAvailabilityQuery()?.key !== query.key) return;
        availabilitySnapshot = { key: query.key, busyRoomNames };
        const rooms = getStoredRooms();
        const availableCount = rooms.filter(room => !busyRoomNames.has(room.name.trim().toLocaleLowerCase("vi"))).length;
        availabilityMessage.textContent = availableCount
            ? `Còn ${availableCount}/${rooms.length} phòng trống trong khung giờ đã chọn.`
            : "Không còn phòng trống trong khung giờ đã chọn.";
        availabilityMessage.className = `availability-message${availableCount ? "" : " is-error"}`;
        renderRoomTable();
    } catch (error) {
        if (request !== availabilitySequence) return;
        availabilitySnapshot = null;
        availabilityMessage.textContent = error.message || "Không thể kiểm tra phòng trống.";
        availabilityMessage.className = "availability-message is-error";
        renderRoomTable();
    } finally {
        if (request === availabilitySequence) checkAvailabilityButton.disabled = false;
    }
}

function normalizeRoom(room) {
    return {
        ...room,
        capacity: Number(room.capacity) || 0,
        devices: Array.isArray(room.devices) ? room.devices.filter(Boolean) : []
    };
}

function getStoredRooms() { return window.RoomAvailability.getRooms(); }

function saveRooms(rooms) {
    localStorage.setItem(ROOM_STORAGE_KEY, JSON.stringify(rooms));
}

function showError(message) {
    roomError.hidden = false;
    roomError.textContent = message;
    roomSuccess.hidden = true;
}

function showSuccess(message) {
    roomSuccess.hidden = false;
    roomSuccess.textContent = message;
    roomError.hidden = true;
}

function clearMessages() {
    roomError.hidden = true;
    roomSuccess.hidden = true;
    roomError.textContent = "";
    roomSuccess.textContent = "";
}

function getNextRoomId(rooms) {
    return rooms.reduce((max, room) => Math.max(max, Number(room.id) || 0), 0) + 1;
}

function getSelectedDevices() {
    return roomDeviceCheckboxes
        .filter(input => input.checked)
        .map(input => input.value.trim())
        .filter(Boolean);
}

function setSelectedDevices(devices = []) {
    roomDeviceCheckboxes.forEach(input => {
        input.checked = devices.includes(input.value);
    });
}

function resetForm() {
    form.reset();
    roomIdInput.value = "";
    setSelectedDevices([]);
    formTitle.textContent = "Thêm phòng họp";
    clearMessages();
}

function validateRoom({ name, location, capacity, devices }) {
    if (!name || !name.trim()) {
        throw new Error("Tên phòng không được để trống.");
    }
    if (name.trim().length < 2) {
        throw new Error("Tên phòng phải có ít nhất 2 ký tự.");
    }
    if (!location || !location.trim()) {
        throw new Error("Vị trí phòng không được để trống.");
    }
    if (!Number.isInteger(capacity) || capacity < 1 || capacity > 200) {
        throw new Error("Sức chứa phải là số nguyên từ 1 đến 200 người.");
    }
    if (!Array.isArray(devices) || devices.length === 0) {
        throw new Error("Vui lòng chọn ít nhất một thiết bị cho phòng.");
    }
}

function renderRoomTable() {
    const rooms = getStoredRooms();
    const total = rooms.length;
    const search = document.getElementById('roomSearch').value.trim().toLocaleLowerCase('vi');
    let count;
    try { count = window.RoomAvailability.participantCount(document.getElementById('filterCapacity').value); }
    catch (error) { availabilityMessage.textContent = error.message; availabilityMessage.className = 'availability-message is-error'; roomTableBody.replaceChildren(); roomCount.textContent = '0/' + total; return; }
    const device = document.getElementById('filterDevice').value;
    const currentSnapshot = availabilitySnapshot && getAvailabilityQuery()?.key === availabilitySnapshot.key;
    const status = currentSnapshot ? document.getElementById('filterStatus').value : '';
    const filtered = rooms.filter(room => (room.name + ' ' + room.location).toLocaleLowerCase('vi').includes(search)
        && room.capacity >= count && (!device || room.devices.includes(device))
        && (!status || getRoomAvailability(room).state === status)).sort((a,b) => a.capacity-b.capacity);
    roomCount.textContent = filtered.length + '/' + total;

    if (!filtered.length) {
        roomTableBody.innerHTML = `
            <tr>
                <td colspan="6" class="empty-state">${total ? "Không có phòng phù hợp. Hãy thay đổi bộ lọc hoặc khung giờ." : "Chưa có phòng nào. Hãy thêm phòng mới."}</td>
            </tr>
        `;
        return;
    }

    roomTableBody.innerHTML = filtered.map(room => {
        const devices = Array.isArray(room.devices) && room.devices.length ? room.devices : ["Chưa có thiết bị"];
        return `
            <tr>
                <td>
                    <div class="room-name">${escapeHtml(room.name)}</div>
                </td>
                <td>
                    <div>${escapeHtml(room.location)}</div>
                </td>
                <td>
                    <span class="capacity-pill"><i class="fa-solid fa-user"></i> ${room.capacity}</span>
                </td>
                <td>
                    <div class="room-meta">${devices.map(device => `<span class="device-tag">${escapeHtml(device)}</span>`).join("")}</div>
                </td>
                <td><span class="availability-pill is-${getRoomAvailability(room).state}">${getRoomAvailability(room).label}</span></td>
                <td>
                    <div class="actions">
                        <button class="btn btn-secondary" type="button" data-action="select" data-id="${escapeHtml(room.id)}" ${getRoomAvailability(room).state !== "free" ? 'disabled' : ''} title="Kiểm tra khung giờ trước khi chọn">Chọn phòng</button>
                        <button class="icon-btn details" type="button" data-action="details" data-id="${room.id}" title="Xem chi tiết phòng" aria-label="Xem chi tiết phòng">
                            <i class="fa-regular fa-eye"></i>
                        </button>
                        <button class="icon-btn edit" type="button" data-action="edit" data-id="${room.id}" title="Sửa phòng">
                            <i class="fa-solid fa-pen"></i>
                        </button>
                        <button class="icon-btn delete" type="button" data-action="delete" data-id="${room.id}" title="Xóa phòng">
                            <i class="fa-solid fa-trash"></i>
                        </button>
                    </div>
                </td>
            </tr>
        `;
    }).join("");
}

function populateEditRoom(roomId) {
    const rooms = getStoredRooms();
    const room = rooms.find(item => Number(item.id) === Number(roomId));
    if (!room) {
        showError("Không tìm thấy phòng cần sửa.");
        return;
    }

    roomIdInput.value = room.id;
    roomNameInput.value = room.name;
    roomLocationInput.value = room.location;
    roomCapacityInput.value = room.capacity;
    setSelectedDevices(room.devices || []);
    formTitle.textContent = "Cập nhật phòng họp";
    clearMessages();
}

function handleSubmit(event) {
    event.preventDefault();
    clearMessages();

    try {
        const rooms = getStoredRooms();
        const selectedDevices = getSelectedDevices();
        const payload = {
            id: roomIdInput.value ? Number(roomIdInput.value) : getNextRoomId(rooms),
            name: roomNameInput.value.trim(),
            location: roomLocationInput.value.trim(),
            capacity: Number(roomCapacityInput.value),
            devices: selectedDevices
        };

        validateRoom(payload);

        const existingIndex = rooms.findIndex(room => Number(room.id) === Number(payload.id));
        if (existingIndex >= 0) {
            rooms[existingIndex] = payload;
            showSuccess("Cập nhật phòng họp thành công.");
        } else {
            rooms.push(payload);
            showSuccess("Thêm phòng họp thành công.");
        }

        saveRooms(rooms);
        renderRoomTable();
        setTimeout(() => {
            resetForm();
        }, 800);
    } catch (error) {
        showError(error.message || "Dữ liệu phòng không hợp lệ.");
    }
}

function handleTableActions(event) {
    const button = event.target.closest("button[data-action]");
    if (!button) return;

    const roomId = Number(button.dataset.id);
    const action = button.dataset.action;
    const rooms = getStoredRooms();

    if (action === "select") {
        const room = rooms.find(item => Number(item.id) === roomId);
        const query = getAvailabilityQuery();
        if (!room || !query || getRoomAvailability(room).state !== 'free') return;
        let count;
        try { count = window.RoomAvailability.participantCount(document.getElementById('filterCapacity').value); }
        catch (error) { showError(error.message); return; }
        if (room.capacity < count) { showError('Phòng không đủ sức chứa. Vui lòng chọn lại.'); renderRoomTable(); return; }
        window.location.href = window.RoomAvailability.selectionUrl(room, query, count);
        return;
    }
    if (action === "details") {
        const room = rooms.find(item => Number(item.id) === roomId);
        if (!room) {
            showError("Không tìm thấy thông tin phòng.");
            return;
        }

        roomDetailsName.textContent = room.name;
        roomDetailsLocation.textContent = room.location || "Chưa cập nhật";
        roomDetailsCapacity.textContent = `${room.capacity} người`;
        roomDetailsAvailability.textContent = getRoomAvailability(room).label;
        roomDetailsDevices.replaceChildren();
        if (room.devices.length) {
            room.devices.forEach(device => {
                const tag = document.createElement("span");
                tag.className = "device-tag";
                tag.textContent = device;
                roomDetailsDevices.append(tag);
            });
        } else {
            roomDetailsDevices.textContent = "Chưa có thiết bị";
        }
        roomDetailsDialog.showModal();
        return;
    }

    if (action === "edit") {
        populateEditRoom(roomId);
        return;
    }

    if (action === "delete") {
        const target = rooms.find(room => Number(room.id) === roomId);
        if (!target) {
            showError("Không tìm thấy phòng để xóa.");
            return;
        }

        const confirmed = window.confirm(`Bạn có chắc muốn xóa phòng "${target.name}"?`);
        if (!confirmed) return;

        const updatedRooms = rooms.filter(room => Number(room.id) !== roomId);
        saveRooms(updatedRooms);
        renderRoomTable();
        resetForm();
        showSuccess("Xóa phòng thành công.");
    }
}

form.addEventListener("submit", handleSubmit);
roomTableBody.addEventListener("click", handleTableActions);
resetButton.addEventListener("click", resetForm);
document.getElementById("closeRoomDetails").addEventListener("click", () => roomDetailsDialog.close());
roomDetailsDialog.addEventListener("click", event => {
    if (event.target === roomDetailsDialog) roomDetailsDialog.close();
});
checkAvailabilityButton.addEventListener("click", checkRoomAvailability);
[availabilityDate, availabilityStart, availabilityEnd].forEach(input => {
    input.addEventListener("input", () => {
        ++availabilitySequence; checkAvailabilityButton.disabled = false;
        if (availabilitySnapshot && getAvailabilityQuery()?.key !== availabilitySnapshot.key) {
            availabilityMessage.textContent = "Khung giờ đã thay đổi. Hãy kiểm tra lại để cập nhật tình trạng phòng.";
            availabilityMessage.className = "availability-message";
            renderRoomTable();
        }
    });
});

const roomNavigation = new URLSearchParams(window.location.search);
const incomingRange = window.RoomAvailability.range(roomNavigation.get('date') || '', roomNavigation.get('startTime') || '', roomNavigation.get('endTime') || '');
if (incomingRange) {
    availabilityDate.value = incomingRange.date;
    availabilityStart.value = incomingRange.startTime;
    availabilityEnd.value = incomingRange.endTime;
}
try {
    const incomingCount = roomNavigation.get('participantCount');
    if (incomingCount !== null) document.getElementById('filterCapacity').value = window.RoomAvailability.participantCount(incomingCount, false);
} catch (_) {}
if (incomingRange) checkRoomAvailability();
else renderRoomTable();
resetForm();

window.meetingRoomOptions = () => getStoredRooms().map(room => ({
    id: room.id,
    name: room.name,
    location: room.location,
    capacity: room.capacity,
    devices: room.devices || []
}));

['roomSearch','filterCapacity','filterDevice','filterStatus'].forEach(id => document.getElementById(id).addEventListener('input', renderRoomTable));
document.getElementById('clearRoomFilters').addEventListener('click', () => {
    ['roomSearch','filterCapacity','filterDevice'].forEach(id => document.getElementById(id).value = '');
    document.getElementById('filterStatus').value = 'free';
    renderRoomTable();
});
window.addEventListener('storage', event => { if (event.key === ROOM_STORAGE_KEY || event.key === window.RoomAvailability.DEMO_KEY || event.key === null) { ++availabilitySequence; availabilitySnapshot = null; checkAvailabilityButton.disabled = false; availabilityMessage.textContent = 'Danh sách phòng đã thay đổi. Vui lòng kiểm tra lại.'; renderRoomTable(); } });
