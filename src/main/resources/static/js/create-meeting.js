document.addEventListener("DOMContentLoaded", function () {
    const subItems = document.querySelectorAll(".sub-item");
    const form = document.getElementById("createMeetingForm");
    const cancelBtn = document.querySelector(".btn-cancel");
    const parentItem = document.querySelector(".menu-item.parent");
    const menuGroup = document.querySelector(".menu-group");
    const roomSelect = document.getElementById("meetingRoom");
    const participantCountInput = document.getElementById("participantCount");
    const equipmentOptions = document.getElementById("equipmentOptions");
    const roomCapacityWarning = document.getElementById("roomCapacityWarning");
    const meetingDateInput = document.getElementById("meetingDate");
    const startTimeInput = document.getElementById("startTime");
    const endTimeInput = document.getElementById("endTime");
    const roomAvailabilityStatus = document.getElementById("roomAvailabilityStatus");

    const apiBaseUrl = window.MEETING_API_BASE_URL
        || (window.location.port === "5500" ? "http://localhost:8080" : "");
    const apiUrl = `${apiBaseUrl}/api/meetings`;

    const urlParams = new URLSearchParams(window.location.search);
    const editMeetingId = urlParams.get("meetingId") || urlParams.get("id");
    const isEditMode = Boolean(editMeetingId);
    let roomAvailability = { key: "", status: "idle", busyRoomNames: new Set() };
    let availabilityRequestSequence = 0;
    let availabilityTimer;
    let equipmentInventory = [];
    let equipmentLoadStatus = "loading";
    let equipmentRefreshTimer;
    let equipmentRequestSequence = 0;
    const selectedEquipmentIds = new Set();

    function toIso(dateValue, timeValue) {
        if (!dateValue || !timeValue) return "";
        return new Date(`${dateValue}T${timeValue}:00`).toISOString();
    }

    function getRoomOptions() {
        try {
            const savedRooms = JSON.parse(localStorage.getItem("meetingRooms") || "[]");
            if (Array.isArray(savedRooms) && savedRooms.length) {
                return savedRooms.map(room => ({
                    ...room,
                    capacity: Number(room.capacity) || 0,
                    devices: Array.isArray(room.devices) ? room.devices.filter(Boolean) : []
                }));
            }
        } catch (error) {
            console.warn("Không thể đọc danh sách phòng đã lưu:", error);
        }
        return [
            { id: 1, name: "Phòng họp A1", location: "Tầng 2, khu A", capacity: 8, devices: ["Máy chiếu", "TV", "Bảng trắng"] },
            { id: 2, name: "Phòng họp B2", location: "Tầng 3, khu B", capacity: 12, devices: ["Máy chiếu", "Micro", "Camera", "Wi‑Fi"] },
            { id: 3, name: "Phòng họp C1", location: "Tầng 1, khu C", capacity: 6, devices: ["TV", "Bảng trắng"] }
        ];
    }

    function getRequestedTimeRange() {
        const date = meetingDateInput?.value || "";
        const start = startTimeInput?.value || "";
        const end = endTimeInput?.value || "";
        if (!date || !start || !end) return null;

        const startValue = Date.parse(toIso(date, start));
        const endValue = Date.parse(toIso(date, end));
        if (!Number.isFinite(startValue) || !Number.isFinite(endValue) || endValue <= startValue) return null;

        return { date, start, end, startValue, endValue, key: `${date}|${start}|${end}` };
    }

    function setRoomAvailabilityMessage(message, state = "") {
        if (!roomAvailabilityStatus) return;
        roomAvailabilityStatus.textContent = message;
        roomAvailabilityStatus.className = `room-availability-status${state ? ` is-${state}` : ""}`;
    }

    function renderRoomOptions() {
        if (!roomSelect) return;

        const previousValue = roomSelect.value;
        const rooms = getRoomOptions();
        const range = getRequestedTimeRange();
        const currentResult = range && roomAvailability.key === range.key;

        roomSelect.replaceChildren(new Option("Chọn phòng họp", ""));
        rooms.forEach(room => {
            const busy = currentResult && roomAvailability.status === "ready"
                && roomAvailability.busyRoomNames.has(room.name.trim().toLocaleLowerCase("vi"));
            const roomLabel = `${room.name} · ${room.capacity} người${room.location ? ` · ${room.location}` : ""}`;
            const option = new Option(busy ? `${roomLabel} · Đã được đặt` : roomLabel, room.id);
            option.disabled = Boolean(busy);
            roomSelect.append(option);
        });

        const selectedRoomStillAvailable = previousValue
            && rooms.some(room => String(room.id) === previousValue)
            && !roomSelect.querySelector(`option[value="${CSS.escape(previousValue)}"]`)?.disabled;
        roomSelect.value = selectedRoomStillAvailable ? previousValue : "";
        if (previousValue && !roomSelect.value) renderRoomDeviceSummary();
    }

    async function refreshRoomAvailability() {
        clearTimeout(availabilityTimer);
        const range = getRequestedTimeRange();
        const requestSequence = ++availabilityRequestSequence;
        if (!range) {
            roomAvailability = { key: "", status: "idle", busyRoomNames: new Set() };
            setRoomAvailabilityMessage("Nhập ngày, giờ bắt đầu và giờ kết thúc hợp lệ để kiểm tra phòng trống.");
            renderRoomOptions();
            return false;
        }

        roomAvailability = { key: range.key, status: "loading", busyRoomNames: new Set() };
        setRoomAvailabilityMessage("Đang kiểm tra phòng trống…");
        renderRoomOptions();

        try {
            const response = await fetch(apiUrl, { headers: { Accept: "application/json" } });
            if (!response.ok) throw new Error(`Không thể kiểm tra phòng trống (HTTP ${response.status}).`);

            const meetings = await response.json();
            if (requestSequence !== availabilityRequestSequence) return false;

            const busyRoomNames = new Set();
            meetings.filter(meeting => {
                if (String(meeting.id) === String(editMeetingId) || meeting.status === "CANCELLED") return false;
                const start = Date.parse(meeting.startTime);
                const end = Date.parse(meeting.endTime);
                return Number.isFinite(start) && Number.isFinite(end)
                    && start < range.endValue && end > range.startValue;
            }).forEach(meeting => {
                if (meeting.room) busyRoomNames.add(meeting.room.trim().toLocaleLowerCase("vi"));
            });

            roomAvailability = { key: range.key, status: "ready", busyRoomNames };
            renderRoomOptions();
            const rooms = getRoomOptions();
            const availableCount = rooms.filter(room => !busyRoomNames.has(room.name.trim().toLocaleLowerCase("vi"))).length;
            setRoomAvailabilityMessage(
                availableCount
                    ? `Còn ${availableCount}/${rooms.length} phòng trống trong khung giờ đã chọn.`
                    : "Không còn phòng trống trong khung giờ đã chọn.",
                availableCount ? "success" : "error"
            );
            return true;
        } catch (error) {
            if (requestSequence !== availabilityRequestSequence) return false;
            roomAvailability = { key: range.key, status: "error", busyRoomNames: new Set() };
            setRoomAvailabilityMessage(error.message || "Không thể kiểm tra phòng trống.", "error");
            renderRoomOptions();
            return false;
        }
    }

    function scheduleRoomAvailabilityCheck() {
        clearTimeout(availabilityTimer);
        const range = getRequestedTimeRange();
        if (!range) {
            roomAvailability = { key: "", status: "idle", busyRoomNames: new Set() };
            setRoomAvailabilityMessage("Nhập ngày, giờ bắt đầu và giờ kết thúc hợp lệ để kiểm tra phòng trống.");
            renderRoomOptions();
            return;
        }

        roomAvailability = { key: range.key, status: "loading", busyRoomNames: new Set() };
        setRoomAvailabilityMessage("Đang kiểm tra phòng trống…");
        availabilityTimer = setTimeout(refreshRoomAvailability, 250);
    }

    function getSelectedRoom() {
        return getRoomOptions().find(item => String(item.id) === String(roomSelect?.value));
    }

    function getSelectedEquipment() {
        return Array.from(selectedEquipmentIds);
    }

    function getRoomEquipment(room) {
        const roomKeys = [room.id, room.name]
            .map(value => String(value || "").trim().toLocaleLowerCase("vi"));

        return equipmentInventory.filter(equipment => {
            const equipmentRoom = String(equipment.roomId || "").trim().toLocaleLowerCase("vi");
            return roomKeys.includes(equipmentRoom);
        });
    }

    function createPreviewEquipmentInventory() {
        return getRoomOptions().flatMap(room => (room.devices || []).map((name, index) => ({
            equipmentId: `preview-${room.id}-${index}`,
            roomId: String(room.id),
            equipmentName: name,
            totalQuantity: 1,
            status: index % 3 === 1 ? "BOOKED" : index % 3 === 2 ? "MAINTENANCE" : "AVAILABLE",
            isPreview: true
        })));
    }

    function getEquipmentAvailability(equipment) {
        const quantity = Number(equipment.availableQuantity ?? equipment.totalQuantity) || 0;
        const status = String(equipment.availabilityStatus || equipment.status || "").trim();
        const normalizedStatus = status.toLocaleLowerCase("vi");
        const maintenance = /maintenance|under repair|bảo trì|đang sửa|sửa chữa/.test(normalizedStatus);
        const booked = /booked|busy|in use|đã đặt|đang sử dụng|bận/.test(normalizedStatus);
        const unavailable = quantity <= 0
            || maintenance
            || booked
            || /unavailable|inactive|broken|hỏng|ngưng hoạt động|không khả dụng/.test(normalizedStatus);

        const label = maintenance
            ? "Bảo trì"
            : booked
                ? "Đã đặt"
                : quantity <= 0
                    ? "Hết thiết bị"
                    : unavailable
                        ? "Không khả dụng"
                        : "Có sẵn";
        const state = maintenance
            ? "maintenance"
            : booked
                ? "booked"
                : quantity <= 0
                    ? "out-of-stock"
                    : unavailable
                        ? "unavailable"
                        : "available";

        return { quantity, unavailable, label, state };
    }

    async function loadEquipmentInventory(requestSequence = ++equipmentRequestSequence) {
        equipmentLoadStatus = "loading";
        renderEquipmentOptions(getSelectedRoom());

        try {
            const params = new URLSearchParams();
            const room = getSelectedRoom();
            const range = getRequestedTimeRange();
            if (room) params.set("roomId", room.id);
            if (range) {
                params.set("startTime", toIso(range.date, range.start));
                params.set("endTime", toIso(range.date, range.end));
            }
            const query = params.toString();
            const response = await fetch(`${apiBaseUrl}/api/equipment${query ? `?${query}` : ""}`, {
                headers: { Accept: "application/json" }
            });
            if (!response.ok) throw new Error(`Không thể tải thiết bị (HTTP ${response.status}).`);

            const inventory = await response.json();
            if (!Array.isArray(inventory)) throw new Error("Danh sách thiết bị không hợp lệ.");
            if (requestSequence !== equipmentRequestSequence) return;
            equipmentInventory = inventory;
            equipmentLoadStatus = "ready";
        } catch (error) {
            if (requestSequence !== equipmentRequestSequence) return;
            equipmentInventory = createPreviewEquipmentInventory();
            equipmentLoadStatus = "demo";
            console.warn("Không thể tải thiết bị khả dụng:", error);
        }

        renderEquipmentOptions(getSelectedRoom());
    }

    function scheduleEquipmentRefresh() {
        clearTimeout(equipmentRefreshTimer);
        const requestSequence = ++equipmentRequestSequence;
        equipmentLoadStatus = "loading";
        renderEquipmentOptions(getSelectedRoom());
        equipmentRefreshTimer = setTimeout(() => loadEquipmentInventory(requestSequence), 250);
    }

    function renderEquipmentOptions(room) {
        if (!equipmentOptions) return;

        const selected = new Set(getSelectedEquipment());
        equipmentOptions.replaceChildren();
        if (!room) {
            const message = document.createElement("p");
            message.className = "equipment-empty";
            message.textContent = "Chọn phòng để xem thiết bị khả dụng.";
            equipmentOptions.append(message);
            return;
        }

        if (equipmentLoadStatus === "loading") {
            const message = document.createElement("p");
            message.className = "equipment-empty";
            message.textContent = "Đang tải thiết bị khả dụng…";
            equipmentOptions.append(message);
            return;
        }

        if (equipmentLoadStatus === "error") {
            const message = document.createElement("p");
            message.className = "equipment-empty";
            message.textContent = "Không tải được danh sách thiết bị. Vui lòng thử lại.";
            equipmentOptions.append(message);
            return;
        }

        if (equipmentLoadStatus === "demo") {
            const message = document.createElement("p");
            message.className = "equipment-data-note";
            message.textContent = "Dữ liệu minh họa, chưa đồng bộ backend.";
            equipmentOptions.append(message);
        }

        const roomEquipment = getRoomEquipment(room);
        if (!roomEquipment.length) {
            const message = document.createElement("p");
            message.className = "equipment-empty";
            message.textContent = "Phòng này chưa có thiết bị trong danh sách.";
            equipmentOptions.append(message);
            return;
        }

        const selectableEquipmentIds = new Set(roomEquipment
            .filter(equipment => !getEquipmentAvailability(equipment).unavailable)
            .map(equipment => String(equipment.equipmentId)));
        selectedEquipmentIds.forEach(equipmentId => {
            if (!selectableEquipmentIds.has(equipmentId)) selectedEquipmentIds.delete(equipmentId);
        });

        roomEquipment.forEach(equipment => {
            const availability = getEquipmentAvailability(equipment);
            const label = document.createElement("label");
            label.className = `checkbox-label${availability.unavailable ? " is-unavailable" : ""}`;
            const checkbox = document.createElement("input");
            checkbox.type = "checkbox";
            checkbox.name = "equipment";
            checkbox.value = equipment.equipmentId;
            checkbox.checked = !availability.unavailable && selected.has(String(equipment.equipmentId));
            checkbox.disabled = availability.unavailable;
            checkbox.addEventListener("change", () => {
                if (checkbox.checked) selectedEquipmentIds.add(String(equipment.equipmentId));
                else selectedEquipmentIds.delete(String(equipment.equipmentId));
            });

            const info = document.createElement("span");
            info.className = "equipment-option-info";
            const name = document.createElement("span");
            name.className = "equipment-name";
            name.textContent = equipment.equipmentName || "Thiết bị chưa đặt tên";
            const details = document.createElement("span");
            details.className = "equipment-option-details";
            const quantity = document.createElement("span");
            quantity.className = "equipment-quantity";
            quantity.textContent = `${availability.quantity} trong kho`;
            const status = document.createElement("span");
            status.className = `equipment-status-pill is-${availability.state}`;
            status.textContent = availability.label;
            details.append(quantity, status);
            info.append(name, details);
            label.append(checkbox, info);
            equipmentOptions.append(label);
        });
    }

    function updateCapacityWarning() {
        if (!roomCapacityWarning || !participantCountInput) return;
        const room = getSelectedRoom();
        const count = Number(participantCountInput.value || 0);

        if (room && count > room.capacity) {
            roomCapacityWarning.textContent = `Phòng ${room.name} chỉ có sức chứa ${room.capacity} người. Hiện bạn nhập ${count} người.`;
            roomCapacityWarning.hidden = false;
            participantCountInput.setAttribute("aria-invalid", "true");
            return;
        }

        roomCapacityWarning.hidden = true;
        roomCapacityWarning.textContent = "";
        participantCountInput.removeAttribute("aria-invalid");
    }

    function renderRoomDeviceSummary() {
        const summary = document.getElementById("roomDeviceSummary");
        if (!roomSelect || !summary) return;

        const room = getSelectedRoom();

        if (!room) {
            summary.style.display = "none";
            summary.textContent = "";
            renderEquipmentOptions(null);
            updateCapacityWarning();
            return;
        }

        summary.style.display = "block";
        const location = room.location ? `${room.location} · ` : "";
        const devices = room.devices.length ? room.devices.join(", ") : "Chưa khai báo";
        summary.textContent = `${location}Sức chứa: ${room.capacity} người · Thiết bị: ${devices}`;
        renderEquipmentOptions(room);
        updateCapacityWarning();
    }

    function populateRoomOptions() {
        if (!roomSelect) return;
        renderRoomOptions();
        renderRoomDeviceSummary();
    }

    function getMeetingPayload() {
        const title = document.getElementById("meetingTitle")?.value?.trim() || "";
        const meetingDate = document.getElementById("meetingDate")?.value || "";
        const startTime = document.getElementById("startTime")?.value || "";
        const endTime = document.getElementById("endTime")?.value || "";
        const roomId = document.getElementById("meetingRoom")?.value || "";
        const organizerId = document.getElementById("organizerId")?.value?.trim() || "user01";
        const notes = document.getElementById("meetingNotes")?.value?.trim() || "";
        const participantCount = Number(document.getElementById("participantCount")?.value || 0);

        if (!title || !meetingDate || !startTime || !endTime || (!isEditMode && !roomId)) {
            throw new Error("Vui lòng điền đầy đủ thông tin bắt buộc.");
        }

        const selectedRoom = getSelectedRoom();
        if (selectedRoom && participantCount > 0 && participantCount > selectedRoom.capacity) {
            throw new Error(`Số người tham gia (${participantCount}) vượt quá sức chứa của phòng ${selectedRoom.name} (${selectedRoom.capacity} người).`);
        }

        return {
            title,
            description: notes || `Cuộc họp ${title}`,
            organizerId,
            startTime: toIso(meetingDate, startTime),
            endTime: toIso(meetingDate, endTime),
            status: "SCHEDULED",
            participantCount,
            room: selectedRoom ? selectedRoom.name : "",
            devices: getSelectedEquipment()
                .map(equipmentId => equipmentInventory.find(item => String(item.equipmentId) === String(equipmentId)))
                .filter(equipment => equipment && !equipment.isPreview)
                .map(equipment => equipment.equipmentName)
        };
    }

    async function bookEquipment(meetingId, payload) {
        const selectedRoom = getSelectedRoom();
        const selectedEquipment = getSelectedEquipment().filter(equipmentId => {
            const equipment = equipmentInventory.find(item => String(item.equipmentId) === equipmentId);
            return equipment && !equipment.isPreview;
        });
        if (!selectedRoom || selectedEquipment.length === 0) return [];

        return Promise.all(selectedEquipment.map(async equipmentId => {
            const response = await fetch(`${apiBaseUrl}/api/equipment/bookings`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Accept": "application/json"
                },
                body: JSON.stringify({
                    equipmentId,
                    meetingId,
                    room: selectedRoom.name,
                    quantity: 1,
                    startTime: payload.startTime,
                    endTime: payload.endTime
                })
            });

            const result = await response.json().catch(() => null);
            if (!response.ok) {
                throw new Error(result?.message || `Không thể đặt thiết bị (HTTP ${response.status}).`);
            }
            return result;
        }));
    }

    async function loadMeetingForEdit(id) {
        try {
            const response = await fetch(`${apiUrl}/${id}`);
            if (!response.ok) {
                throw new Error("Không thể tải thông tin cuộc họp để chỉnh sửa.");
            }
            const meeting = await response.json();

            // Populate form
            const titleInput = document.getElementById("meetingTitle");
            const dateInput = document.getElementById("meetingDate");
            const startInput = document.getElementById("startTime");
            const endInput = document.getElementById("endTime");
            const orgInput = document.getElementById("organizerId");
            const notesInput = document.getElementById("meetingNotes");
            const pageTitle = document.getElementById("pageTitleHeading");
            const pageSubtitle = document.getElementById("pageSubtitle");
            const submitBtnText = document.getElementById("submitBtnText");
            const submitBtnIcon = document.getElementById("submitBtnIcon");

            if (titleInput) titleInput.value = meeting.title || "";
            if (orgInput) orgInput.value = meeting.organizerId || "user01";
            if (notesInput) notesInput.value = meeting.description || "";

            if (meeting.startTime) {
                const start = new Date(meeting.startTime);
                if (dateInput) {
                    const pad = n => String(n).padStart(2, "0");
                    dateInput.value = `${start.getFullYear()}-${pad(start.getMonth() + 1)}-${pad(start.getDate())}`;
                }
                if (startInput) {
                    const pad = n => String(n).padStart(2, "0");
                    startInput.value = `${pad(start.getHours())}:${pad(start.getMinutes())}`;
                }
            }

            if (meeting.endTime) {
                const end = new Date(meeting.endTime);
                if (endInput) {
                    const pad = n => String(n).padStart(2, "0");
                    endInput.value = `${pad(end.getHours())}:${pad(end.getMinutes())}`;
                }
            }

            if (pageTitle) pageTitle.textContent = "Chỉnh sửa lịch họp";
            if (pageSubtitle) pageSubtitle.textContent = `Cập nhật thông tin cuộc họp #${meeting.id}`;
            if (submitBtnText) submitBtnText.textContent = "Cập nhật lịch họp";
            if (submitBtnIcon) {
                submitBtnIcon.className = "fa-solid fa-floppy-disk";
            }
        } catch (error) {
            console.error("Load meeting for edit error:", error);
            alert(error.message);
        }
    }

    populateRoomOptions();
    loadEquipmentInventory();
    participantCountInput?.addEventListener("input", updateCapacityWarning);
    roomSelect?.addEventListener("change", () => {
        selectedEquipmentIds.clear();
        renderRoomDeviceSummary();
        scheduleEquipmentRefresh();
    });
    [meetingDateInput, startTimeInput, endTimeInput].forEach(input => {
        input?.addEventListener("change", () => {
            scheduleRoomAvailabilityCheck();
            scheduleEquipmentRefresh();
        });
    });

    if (isEditMode) {
        loadMeetingForEdit(editMeetingId);
    }

    async function createMeeting() {
        const payload = getMeetingPayload();

        const response = await fetch(apiUrl, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Accept": "application/json"
            },
            body: JSON.stringify(payload)
        });

        const result = await response.json().catch(() => null);
        if (!response.ok) {
            const message = result?.message || result?.error || Object.values(result || {}).join("; ") || `Yêu cầu thất bại với mã ${response.status}`;
            throw new Error(message);
        }

        return result;
    }

    async function updateMeeting(id) {
        const payload = getMeetingPayload();
        const organizerId = document.getElementById("organizerId")?.value?.trim() || "user01";

        const response = await fetch(`${apiUrl}/${id}`, {
            method: "PUT",
            headers: {
                "Content-Type": "application/json",
                "Accept": "application/json",
                "X-User-Id": organizerId
            },
            body: JSON.stringify({
                title: payload.title,
                description: payload.description,
                startTime: payload.startTime,
                endTime: payload.endTime
            })
        });

        const result = await response.json().catch(() => null);
        if (!response.ok) {
            const message = result?.message || result?.error || Object.values(result || {}).join("; ") || `Yêu cầu cập nhật thất bại với mã ${response.status}`;
            throw new Error(message);
        }

        return result;
    }

    async function addParticipants(meetingId) {
        const emails = (document.getElementById("participantEmails")?.value || "")
            .split(/[,;\n]/)
            .map(value => value.trim())
            .filter((email, index, list) => email && list.findIndex(item => item.toLowerCase() === email.toLowerCase()) === index);

        const invalidEmail = emails.find(email => !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email));
        if (invalidEmail) {
            throw new Error(`Email không hợp lệ: ${invalidEmail}`);
        }

        const results = await Promise.allSettled(emails.map(async email => {
            const name = email.split("@")[0]
                .replace(/[._+-]+/g, " ")
                .replace(/\b\w/g, character => character.toUpperCase());
            const response = await fetch(`${apiUrl}/${meetingId}/participants`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Accept": "application/json"
                },
                body: JSON.stringify({ name: name || email, email, role: "Tham dự" })
            });
            if (!response.ok) {
                throw new Error(`Không thể thêm ${email}`);
            }
        }));

        return {
            added: results.filter(result => result.status === "fulfilled").length,
            failed: results.filter(result => result.status === "rejected").length
        };
    }

    if (parentItem && menuGroup) {
        const subMenu = menuGroup.querySelector(".sub-menu");
        const arrowIcon = parentItem.querySelector(".arrow-icon");

        parentItem.addEventListener("click", function (e) {
            e.preventDefault();
            const isHidden = subMenu.style.display === "none";
            subMenu.style.display = isHidden ? "flex" : "none";
            if (arrowIcon) {
                arrowIcon.style.transform = isHidden ? "rotate(180deg)" : "rotate(0deg)";
            }
        });
    }

    subItems.forEach(item => {
        item.addEventListener("click", function (e) {
            subItems.forEach(sub => sub.classList.remove("active"));
            this.classList.add("active");
        });
    });

    if (cancelBtn) {
        cancelBtn.addEventListener("click", function () {
            if (confirm("Bạn có chắc chắn muốn hủy form này?")) {
                window.location.href = isEditMode ? "update-meeting.html" : "dashboard.html";
            }
        });
    }

    if (form) {
        form.addEventListener("submit", async function (e) {
            e.preventDefault();

            const submitBtn = form.querySelector('button[type="submit"]');
            if (submitBtn) {
                submitBtn.disabled = true;
                submitBtn.textContent = isEditMode ? "Đang cập nhật..." : "Đang tạo...";
            }

            try {
                if (isEditMode) {
                    const updated = await updateMeeting(editMeetingId);
                    alert("Cập nhật lịch họp thành công!");
                    window.location.href = `update-meeting.html?meetingId=${encodeURIComponent(updated.id)}`;
                } else {
                    const availabilityConfirmed = await refreshRoomAvailability();
                    if (!availabilityConfirmed) {
                        throw new Error("Không thể xác nhận phòng còn trống. Vui lòng kiểm tra lại ngày và giờ.");
                    }
                    const createdMeeting = await createMeeting();
                    const attendees = await addParticipants(createdMeeting.id);
                    let equipmentBookings = [];
                    try {
                        equipmentBookings = await bookEquipment(createdMeeting.id, getMeetingPayload());
                    } catch (error) {
                        alert(`Cuộc họp đã được tạo nhưng thiết bị chưa đặt được: ${error.message}`);
                    }
                    const query = new URLSearchParams({ meetingId: createdMeeting.id });
                    if (attendees.added) query.set("added", attendees.added);
                    if (attendees.failed) query.set("failed", attendees.failed);
                    if (equipmentBookings.length) query.set("equipmentBooked", equipmentBookings.length);
                    window.location.href = `participants.html?${query.toString()}`;
                }
            } catch (error) {
                console.error("Meeting submit error:", error);
                alert(error.message || "Thao tác không thành công. Vui lòng kiểm tra lại dữ liệu.");
            } finally {
                if (submitBtn) {
                    submitBtn.disabled = false;
                    const submitBtnText = document.getElementById("submitBtnText");
                    const submitBtnIcon = document.getElementById("submitBtnIcon");
                    if (isEditMode) {
                        if (submitBtnText) submitBtnText.textContent = "Cập nhật lịch họp";
                        if (submitBtnIcon) submitBtnIcon.className = "fa-solid fa-floppy-disk";
                    } else {
                        if (submitBtnText) submitBtnText.textContent = "Tạo lịch họp";
                        if (submitBtnIcon) submitBtnIcon.className = "fa-regular fa-calendar-plus";
                    }
                }
            }
        });
    }
});