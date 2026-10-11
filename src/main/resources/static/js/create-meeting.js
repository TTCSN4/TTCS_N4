document.addEventListener("DOMContentLoaded", () => {
    const form = document.querySelector("#createMeetingForm");
    const roomSelect = document.querySelector("#meetingRoom");
    const equipmentOptions = document.querySelector("#equipmentOptions");
    const roomAvailabilityStatus = document.querySelector("#roomAvailabilityStatus");
    const roomDeviceSummary = document.querySelector("#roomDeviceSummary");
    const formError = document.querySelector("#formError");
    const meetingDateInput = document.querySelector("#meetingDate");
    const startTimeInput = document.querySelector("#startTime");
    const endTimeInput = document.querySelector("#endTime");
    const recurrenceSelect = document.querySelector("#recurrence");
    const repeatCountInput = document.querySelector("#repeatCount");
    const repeatCountGroup = document.querySelector("#repeatCountGroup");
    const submitButton = form.querySelector('button[type="submit"]');

    let equipmentInventory = [];
    let equipmentStatuses = new Map();
    let selectedEquipmentIds = new Set();
    let roomAvailability = { key: "", status: "idle", busyRooms: new Set() };
    let statusRequestSequence = 0;
    let availabilityRequestSequence = 0;

    async function api(path, options = {}) {
        const response = await fetch(path, {
            ...options,
            headers: {
                Accept: "application/json",
                ...(options.body ? { "Content-Type": "application/json" } : {}),
                ...options.headers
            }
        });
        if (response.status === 204) return null;
        const result = await response.json().catch(() => null);
        if (!response.ok) {
            throw new Error(result?.detail || result?.message || result?.error || `Yêu cầu thất bại (HTTP ${response.status}).`);
        }
        return result;
    }

    function toIso(date, time) {
        return new Date(`${date}T${time}:00`).toISOString();
    }

    function localDateTime(date) {
        const pad = value => String(value).padStart(2, "0");
        return {
            date: `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`,
            time: `${pad(date.getHours())}:${pad(date.getMinutes())}`
        };
    }

    function getRequestedTimeRange() {
        const date = meetingDateInput.value;
        const start = startTimeInput.value;
        const end = endTimeInput.value;
        if (!date || !start || !end) return null;

        const startValue = Date.parse(toIso(date, start));
        const endValue = Date.parse(toIso(date, end));
        if (!Number.isFinite(startValue) || !Number.isFinite(endValue) || endValue <= startValue) return null;
        return { startValue, endValue, key: `${date}|${start}|${end}` };
    }

    function setRoomMessage(message, state = "") {
        roomAvailabilityStatus.textContent = message;
        roomAvailabilityStatus.className = `room-availability-status${state ? ` is-${state}` : ""}`;
    }

    function populateRoomOptions() {
        const currentValue = roomSelect.value;
        const rooms = [...new Set(equipmentInventory
            .map(item => item.roomId?.trim())
            .filter(Boolean))]
            .sort((a, b) => a.localeCompare(b, "vi"));

        roomSelect.replaceChildren(new Option("Chọn phòng họp", ""));
        rooms.forEach(room => roomSelect.append(new Option(room, room)));
        if (rooms.includes(currentValue)) roomSelect.value = currentValue;
        roomSelect.disabled = rooms.length === 0;

        if (!rooms.length) {
            setRoomMessage("Chưa có thiết bị/phòng trong hệ thống. Thêm thiết bị trước khi tạo cuộc họp.", "error");
        }
    }

    async function loadEquipmentInventory() {
        equipmentOptions.replaceChildren();
        const loading = document.createElement("p");
        loading.className = "equipment-empty";
        loading.textContent = "Đang tải thiết bị…";
        equipmentOptions.append(loading);

        try {
            const items = await api("/api/equipment");
            if (!Array.isArray(items)) throw new Error("Dữ liệu thiết bị trả về không hợp lệ.");
            equipmentInventory = items;
            populateRoomOptions();
            if (roomSelect.value) {
                await Promise.all([refreshRoomAvailability(), refreshEquipmentStatus()]);
            } else {
                renderEquipmentOptions("Chọn phòng để xem thiết bị khả dụng.");
            }
        } catch (error) {
            equipmentInventory = [];
            roomSelect.disabled = true;
            renderEquipmentOptions(`Không tải được danh sách thiết bị: ${error.message}`);
            setRoomMessage(`Không tải được danh sách phòng: ${error.message}`, "error");
        }
    }

    async function refreshRoomAvailability() {
        const range = getRequestedTimeRange();
        const sequence = ++availabilityRequestSequence;
        if (!range) {
            roomAvailability = { key: "", status: "idle", busyRooms: new Set() };
            setRoomMessage("Nhập ngày, giờ bắt đầu và giờ kết thúc hợp lệ để kiểm tra phòng trống.");
            return false;
        }

        roomAvailability = { key: range.key, status: "loading", busyRooms: new Set() };
        setRoomMessage("Đang kiểm tra phòng trống…");
        try {
            const meetings = await api("/api/meetings");
            if (sequence !== availabilityRequestSequence) return false;
            const busyRooms = new Set(meetings
                .filter(meeting => meeting.status === "SCHEDULED")
                .filter(meeting => {
                    const start = Date.parse(meeting.startTime);
                    const end = Date.parse(meeting.endTime);
                    return Number.isFinite(start) && Number.isFinite(end)
                        && start < range.endValue && end > range.startValue;
                })
                .map(meeting => String(meeting.room || "").trim().toLocaleLowerCase("vi"))
                .filter(Boolean));

            roomAvailability = { key: range.key, status: "ready", busyRooms };
            const room = roomSelect.value.trim();
            const busy = room && busyRooms.has(room.toLocaleLowerCase("vi"));
            const roomCount = new Set(equipmentInventory.map(item => item.roomId?.trim()).filter(Boolean)).size;
            const availableCount = [...new Set(equipmentInventory.map(item => item.roomId?.trim()).filter(Boolean))]
                .filter(value => !busyRooms.has(value.toLocaleLowerCase("vi"))).length;
            setRoomMessage(
                busy
                    ? `Phòng ${room} đã có cuộc họp trong khung giờ này.`
                    : `Còn ${availableCount}/${roomCount} phòng trống trong khung giờ đã chọn.`,
                busy || availableCount === 0 ? "error" : "success"
            );
            return !busy;
        } catch (error) {
            if (sequence !== availabilityRequestSequence) return false;
            roomAvailability = { key: range.key, status: "error", busyRooms: new Set() };
            setRoomMessage(`Không thể kiểm tra phòng trống: ${error.message}`, "error");
            return false;
        }
    }

    async function refreshEquipmentStatus() {
        const room = roomSelect.value.trim();
        const range = getRequestedTimeRange();
        const sequence = ++statusRequestSequence;
        equipmentStatuses.clear();
        selectedEquipmentIds.clear();

        if (!room) {
            renderEquipmentOptions("Chọn phòng để xem thiết bị khả dụng.");
            renderRoomDeviceSummary();
            return;
        }
        if (!range) {
            renderEquipmentOptions("Nhập thời gian bắt đầu và kết thúc hợp lệ để xem trạng thái.");
            return;
        }

        renderEquipmentOptions("Đang tải trạng thái thiết bị…");
        try {
            const at = new Date(range.startValue).toISOString();
            const result = await api(`/api/equipment/status?room=${encodeURIComponent(room)}&at=${encodeURIComponent(at)}`);
            if (sequence !== statusRequestSequence) return;
            if (!Array.isArray(result)) throw new Error("Dữ liệu trạng thái thiết bị trả về không hợp lệ.");
            equipmentStatuses = new Map(result.map(item => [String(item.equipmentId), item]));
            renderEquipmentOptions();
            renderRoomDeviceSummary();
        } catch (error) {
            if (sequence !== statusRequestSequence) return;
            renderEquipmentOptions(`Không tải được trạng thái thiết bị: ${error.message}`);
        }
    }

    function renderEquipmentOptions(message = "") {
        equipmentOptions.replaceChildren();
        if (message) {
            const hint = document.createElement("p");
            hint.className = "equipment-empty";
            hint.textContent = message;
            equipmentOptions.append(hint);
            return;
        }

        const room = roomSelect.value.trim().toLocaleLowerCase("vi");
        const items = equipmentInventory.filter(item =>
            String(item.roomId || "").trim().toLocaleLowerCase("vi") === room);
        if (!items.length) {
            const hint = document.createElement("p");
            hint.className = "equipment-empty";
            hint.textContent = "Phòng này chưa có thiết bị trong danh sách.";
            equipmentOptions.append(hint);
            return;
        }

        items.forEach(item => {
            const status = equipmentStatuses.get(String(item.equipmentId));
            if (!status) return;
            const available = status.availableQuantity > 0;
            const label = document.createElement("label");
            label.className = `checkbox-label${available ? "" : " is-unavailable"}`;

            const checkbox = document.createElement("input");
            checkbox.type = "checkbox";
            checkbox.name = "equipment";
            checkbox.value = item.equipmentId;
            checkbox.disabled = !available;
            checkbox.checked = available && selectedEquipmentIds.has(String(item.equipmentId));
            checkbox.addEventListener("change", () => {
                if (checkbox.checked) selectedEquipmentIds.add(String(item.equipmentId));
                else selectedEquipmentIds.delete(String(item.equipmentId));
                renderRoomDeviceSummary();
            });

            const info = document.createElement("span");
            info.className = "equipment-option-info";
            const name = document.createElement("span");
            name.className = "equipment-name";
            name.textContent = item.equipmentName;
            const details = document.createElement("span");
            details.className = "equipment-option-details";
            const quantity = document.createElement("span");
            quantity.className = "equipment-quantity";
            quantity.textContent = `${status.availableQuantity}/${status.totalQuantity} có sẵn · ${status.bookedQuantity} đã đặt · ${status.maintenanceQuantity} bảo trì`;
            const badges = document.createElement("span");
            badges.className = "equipment-option-details";
            status.statuses.forEach(value => {
                const badge = document.createElement("span");
                const state = value.toLowerCase().replace("_", "-");
                badge.className = `equipment-status-pill is-${state}`;
                badge.textContent = {
                    AVAILABLE: "Có sẵn",
                    BOOKED: "Đã đặt",
                    MAINTENANCE: "Bảo trì"
                }[value] || value;
                badges.append(badge);
            });
            details.append(quantity, badges);
            info.append(name, details);
            label.append(checkbox, info);
            equipmentOptions.append(label);
        });

        if (!equipmentOptions.childElementCount) {
            const hint = document.createElement("p");
            hint.className = "equipment-empty";
            hint.textContent = "Không nhận được trạng thái cho các thiết bị trong phòng.";
            equipmentOptions.append(hint);
        }
    }

    function renderRoomDeviceSummary() {
        const room = roomSelect.value;
        if (!room) {
            roomDeviceSummary.style.display = "none";
            roomDeviceSummary.textContent = "";
            return;
        }
        const names = [...selectedEquipmentIds]
            .map(id => equipmentInventory.find(item => String(item.equipmentId) === id)?.equipmentName)
            .filter(Boolean);
        roomDeviceSummary.style.display = "block";
        roomDeviceSummary.textContent = names.length
            ? `Thiết bị đã chọn: ${names.join(", ")}`
            : `Phòng ${room} · chọn thiết bị khả dụng bên dưới.`;
    }

    function getMeetingPayload() {
        const title = document.querySelector("#meetingTitle").value.trim();
        const date = meetingDateInput.value;
        const startTime = startTimeInput.value;
        const endTime = endTimeInput.value;
        const room = roomSelect.value.trim();
        const participantCount = Number(document.querySelector("#participantCount").value);
        const participants = document.querySelector("#participantEmails").value
            .split(/[,;\n]/)
            .map(value => value.trim())
            .filter((email, index, list) =>
                email && list.findIndex(item => item.toLowerCase() === email.toLowerCase()) === index);
        const invalidEmail = participants.find(email => !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email));

        if (!title || !date || !startTime || !endTime || !room || participantCount < 1) {
            throw new Error("Vui lòng điền đầy đủ thông tin bắt buộc.");
        }
        if (Date.parse(toIso(date, endTime)) <= Date.parse(toIso(date, startTime))) {
            throw new Error("Giờ kết thúc phải sau giờ bắt đầu.");
        }
        if (invalidEmail) throw new Error(`Email không hợp lệ: ${invalidEmail}`);

        return {
            title,
            description: document.querySelector("#meetingNotes").value.trim() || "",
            organizerId: Number(document.querySelector("#organizerId").value) || 1,
            startTime: toIso(date, startTime),
            endTime: toIso(date, endTime),
            participants,
            recurrence: recurrenceSelect.value,
            repeatCount: recurrenceSelect.value === "NONE" ? 0 : Number(repeatCountInput.value),
            room
        };
    }

    async function createMeeting(payload) {
        return api("/api/meetings", {
            method: "POST",
            body: JSON.stringify(payload)
        });
    }

    async function bookSelectedEquipment(meetings, payload) {
        const bookings = [];
        for (const meeting of meetings) {
            const duration = Date.parse(meeting.endTime) - Date.parse(meeting.startTime);
            for (const equipmentId of selectedEquipmentIds) {
                const startTime = meeting.startTime;
                const endTime = new Date(Date.parse(startTime) + duration).toISOString();
                const booking = await api("/api/equipment/bookings", {
                    method: "POST",
                    body: JSON.stringify({
                        equipmentId,
                        meetingId: meeting.id,
                        room: payload.room,
                        quantity: 1,
                        startTime,
                        endTime
                    })
                });
                bookings.push(booking);
            }
        }
        return bookings;
    }

    async function rollbackMeetings(meetings, bookings, seriesRootId) {
        const failures = [];
        try {
            const allMeetings = await api("/api/meetings");
            const seriesMeetings = allMeetings.filter(meeting =>
                Number(meeting.id) === Number(seriesRootId)
                || Number(meeting.recurrenceSeriesId) === Number(seriesRootId));
            const knownIds = new Set(meetings.map(meeting => Number(meeting.id)));
            seriesMeetings.forEach(meeting => {
                if (!knownIds.has(Number(meeting.id))) meetings.push(meeting);
            });
        } catch (error) {
            failures.push(`Không thể tải danh sách đầy đủ các cuộc họp cần hủy: ${error.message}`);
        }
        for (const booking of bookings.reverse()) {
            try {
                await api(`/api/equipment/bookings/${booking.id}`, { method: "DELETE" });
            } catch (error) {
                failures.push(error.message);
            }
        }
        for (const meeting of [...meetings].reverse()) {
            try {
                await api(`/api/meetings/${meeting.id}`, { method: "DELETE" });
            } catch (error) {
                failures.push(error.message);
            }
        }
        return failures;
    }

    document.querySelector(".btn-cancel").addEventListener("click", () => {
        window.location.assign("/");
    });

    document.querySelector(".menu-item.parent").addEventListener("click", event => {
        event.preventDefault();
        document.querySelector(".menu-group").classList.toggle("collapsed");
    });

    form.addEventListener("submit", async event => {
        event.preventDefault();
        formError.hidden = true;
        formError.textContent = "";
        submitButton.disabled = true;

        try {
            const payload = getMeetingPayload();
            const range = getRequestedTimeRange();
            if (!range || roomAvailability.status !== "ready" || roomAvailability.key !== range.key) {
                const available = await refreshRoomAvailability();
                if (!available) throw new Error(roomAvailability.status === "error"
                    ? "Không thể xác nhận phòng trống. Vui lòng thử lại."
                    : `Phòng ${roomSelect.value} đã được đặt trong khung giờ này.`);
            } else if (roomAvailability.busyRooms.has(payload.room.toLocaleLowerCase("vi"))) {
                throw new Error(`Phòng ${payload.room} đã có cuộc họp trong khung giờ này.`);
            }

            if (selectedEquipmentIds.size > 0
                    && (!equipmentStatuses.size || !equipmentStatuses.has([...selectedEquipmentIds][0]))) {
                await refreshEquipmentStatus();
            }

            const meeting = await createMeeting(payload);
            let meetingsToBook = [meeting];
            const bookings = [];
            if (selectedEquipmentIds.size > 0) {
                try {
                    const scheduledMeetings = await api("/api/meetings");
                    const occurrences = scheduledMeetings.filter(item =>
                        Number(item.id) === Number(meeting.id)
                        || Number(item.recurrenceSeriesId) === Number(meeting.id));
                    if (occurrences.length) meetingsToBook = occurrences;
                    bookings.push(...await bookSelectedEquipment(meetingsToBook, payload));
                } catch (error) {
                    const rollbackFailures = await rollbackMeetings(meetingsToBook, bookings, meeting.id);
                    const rollbackNote = rollbackFailures.length
                        ? ` Không thể hoàn tác đầy đủ: ${rollbackFailures.join("; ")}.`
                        : " Cuộc họp đã được hủy.";
                    throw new Error(`Không thể đặt thiết bị: ${error.message}.${rollbackNote}`);
                }
            }

            window.alert("Đã tạo cuộc họp và đặt thiết bị thành công.");
            window.location.assign("/");
        } catch (error) {
            formError.textContent = error.message;
            formError.hidden = false;
        } finally {
            submitButton.disabled = false;
        }
    });

    roomSelect.addEventListener("change", async () => {
        selectedEquipmentIds.clear();
        renderRoomDeviceSummary();
        await Promise.all([refreshRoomAvailability(), refreshEquipmentStatus()]);
    });

    [meetingDateInput, startTimeInput, endTimeInput].forEach(input => {
        input.addEventListener("change", async () => {
            await Promise.all([refreshRoomAvailability(), refreshEquipmentStatus()]);
        });
        recurrenceSelect.addEventListener("change", () => {
            repeatCountGroup.hidden = recurrenceSelect.value === "NONE";
        });
    });

    const now = new Date();
    const start = new Date(now.getTime() + 60 * 60 * 1000);
    start.setMinutes(Math.ceil(start.getMinutes() / 30) * 30, 0, 0);
    if (start.getHours() === 23) {
        start.setDate(start.getDate() + 1);
        start.setHours(9, 0, 0, 0);
    }
    const end = new Date(start.getTime() + 60 * 60 * 1000);
    const startLocal = localDateTime(start);
    const endLocal = localDateTime(end);
    meetingDateInput.value = startLocal.date;
    startTimeInput.value = startLocal.time;
    endTimeInput.value = endLocal.time;

    loadEquipmentInventory();
});
