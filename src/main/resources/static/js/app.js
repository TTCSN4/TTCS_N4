const elements = {
    rows: document.querySelector("#meetingRows"),
    newTitle: document.querySelector("#newTitle"),
    userId: document.querySelector("#userId"),
    newDescription: document.querySelector("#newDescription"),
    newStartTime: document.querySelector("#newStartTime"),
    newEndTime: document.querySelector("#newEndTime"),
    editButton: document.querySelector("#editButton"),
    cancelButton: document.querySelector("#cancelButton"),
    message: document.querySelector("#message"),
    dialog: document.querySelector("#confirmDialog"),
    confirmText: document.querySelector("#confirmText"),
    closeDialogButton: document.querySelector("#closeDialogButton"),
    confirmCancelButton: document.querySelector("#confirmCancelButton"),
    suggestionDate: document.querySelector("#suggestionDate"),
    participants: document.querySelector("#participants"),
    findSlotsButton: document.querySelector("#findSlotsButton"),
    slotList: document.querySelector("#slotList")
};

let meetings = [];
let selectedMeeting = null;

function showMessage(text, type) {
    if (!elements.message) return;
    elements.message.textContent = text;
    elements.message.className = `message visible ${type}`;
}

function clearMessage() {
    if (!elements.message) return;
    elements.message.textContent = "";
    elements.message.className = "message";
}

function isEditable(meeting) {
    return meeting.status === "SCHEDULED" && new Date(meeting.startTime) > new Date();
}

function statusClass(status) {
    return status === "SCHEDULED" ? "status-scheduled" : status === "CANCELLED" ? "status-cancelled" : "status-other";
}

function statusLabel(status) {
    if (status === "SCHEDULED") return "Active";
    if (status === "CANCELLED") return "Đã hủy";
    if (status === "IN_PROGRESS") return "Đang họp";
    if (status === "COMPLETED") return "Hoàn thành";
    return status;
}

function escapeHtml(value) {
    const node = document.createElement("span");
    node.textContent = value ?? "";
    return node.innerHTML;
}

function formatDate(value) {
    if (!value) return "—";
    const date = new Date(value);
    return isNaN(date.getTime()) ? "—" : date.toLocaleDateString("vi-VN");
}

function toLocalDatetimeInput(isoString) {
    if (!isoString) return "";
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return "";
    const pad = (n) => String(n).padStart(2, "0");
    const year = date.getFullYear();
    const month = pad(date.getMonth() + 1);
    const day = pad(date.getDate());
    const hours = pad(date.getHours());
    const minutes = pad(date.getMinutes());
    return `${year}-${month}-${day}T${hours}:${minutes}`;
}

function fromLocalDatetimeInput(inputValue) {
    if (!inputValue) return null;
    const date = new Date(inputValue);
    return isNaN(date.getTime()) ? null : date.toISOString();
}

async function apiRequest(url, options = {}) {
    const response = await fetch(url, options);
    if (response.ok) return response.status === 204 ? null : response.json();

    let message = "Không thể xử lý yêu cầu.";
    try {
        const error = await response.json();
        message = error.message || message;
    } catch (_) {
        // Giữ thông báo mặc định nếu server không trả JSON.
    }
    throw new Error(message);
}

function renderRows() {
    if (!meetings.length) {
        elements.rows.innerHTML = '<tr><td colspan="6" class="empty">Chưa có cuộc họp</td></tr>';
        return;
    }

    elements.rows.innerHTML = meetings.map(meeting => `
        <tr data-id="${meeting.id}" class="${selectedMeeting?.id === meeting.id ? "selected " : ""}${isEditable(meeting) ? "" : "unavailable"}">
            <td>${meeting.id}</td>
            <td>${escapeHtml(meeting.title)}</td>
            <td>${escapeHtml(meeting.organizerId)}</td>
            <td>${formatDate(meeting.startTime)}</td>
            <td>${escapeHtml(meeting.room || "—")}</td>
            <td><span class="status ${statusClass(meeting.status)}">${statusLabel(meeting.status)}</span></td>
        </tr>`).join("");
}

function selectMeeting(id) {
    selectedMeeting = meetings.find(meeting => meeting.id === id) ?? null;
    if (!selectedMeeting) return;

    if (elements.newTitle) elements.newTitle.value = selectedMeeting.title || "";
    if (elements.userId) elements.userId.value = selectedMeeting.organizerId || "user01";
    if (elements.newDescription) elements.newDescription.value = selectedMeeting.description || "";
    if (elements.newStartTime) elements.newStartTime.value = toLocalDatetimeInput(selectedMeeting.startTime);
    if (elements.newEndTime) elements.newEndTime.value = toLocalDatetimeInput(selectedMeeting.endTime);

    const editable = isEditable(selectedMeeting);
    if (elements.editButton) elements.editButton.disabled = !editable;
    if (elements.cancelButton) elements.cancelButton.disabled = !editable;
    clearMessage();
    if (!editable) {
        showMessage("Cuộc họp không còn hoạt động hoặc đã bắt đầu nên không thể sửa/hủy.", "error");
    }
    renderRows();
}

async function loadMeetings() {
    try {
        meetings = await apiRequest("/api/meetings");
        renderRows();

        // Kiểm tra xem có query parameter id hoặc meetingId không
        const params = new URLSearchParams(window.location.search);
        const meetingIdParam = params.get("meetingId") || params.get("id");
        if (meetingIdParam) {
            const targetId = Number(meetingIdParam);
            if (!isNaN(targetId)) {
                selectMeeting(targetId);
            }
        }
    } catch (error) {
        elements.rows.innerHTML = '<tr><td colspan="6" class="empty">Không tải được dữ liệu</td></tr>';
        showMessage(error.message, "error");
    }
}

if (elements.rows) {
    elements.rows.addEventListener("click", event => {
        const row = event.target.closest("tr[data-id]");
        if (row) selectMeeting(Number(row.dataset.id));
    });
}

if (elements.editButton) {
    elements.editButton.addEventListener("click", async () => {
        clearMessage();
        const title = elements.newTitle ? elements.newTitle.value.trim() : "";
        if (!selectedMeeting || !title || (elements.userId && !elements.userId.reportValidity())) {
            showMessage("Hãy chọn cuộc họp, nhập tên mới và mã nhân viên hợp lệ.", "error");
            return;
        }

        const description = elements.newDescription
            ? elements.newDescription.value.trim()
            : selectedMeeting.description;

        let startTime = selectedMeeting.startTime;
        let endTime = selectedMeeting.endTime;

        if (elements.newStartTime && elements.newStartTime.value) {
            const converted = fromLocalDatetimeInput(elements.newStartTime.value);
            if (converted) startTime = converted;
        }

        if (elements.newEndTime && elements.newEndTime.value) {
            const converted = fromLocalDatetimeInput(elements.newEndTime.value);
            if (converted) endTime = converted;
        }

        elements.editButton.disabled = true;
        try {
            const updated = await apiRequest(`/api/meetings/${selectedMeeting.id}`, {
                method: "PUT",
                headers: {
                    "Content-Type": "application/json",
                    "X-User-Id": elements.userId ? elements.userId.value.trim() : selectedMeeting.organizerId
                },
                body: JSON.stringify({
                    title,
                    description,
                    startTime,
                    endTime
                })
            });
            meetings = meetings.map(item => item.id === updated.id ? updated : item);
            selectedMeeting = updated;
            renderRows();
            showMessage("Đã sửa lịch họp thành công.", "success");
        } catch (error) {
            showMessage(error.message, "error");
        } finally {
            elements.editButton.disabled = !isEditable(selectedMeeting);
        }
    });
}

if (elements.cancelButton) {
    elements.cancelButton.addEventListener("click", () => {
        if (!selectedMeeting || (elements.userId && !elements.userId.reportValidity())) return;
        elements.confirmText.textContent = `Bạn có chắc muốn hủy cuộc họp “${selectedMeeting.title}”?`;
        elements.dialog.showModal();
    });
}

if (elements.closeDialogButton) {
    elements.closeDialogButton.addEventListener("click", () => elements.dialog.close());
}

if (elements.confirmCancelButton) {
    elements.confirmCancelButton.addEventListener("click", async () => {
        elements.confirmCancelButton.disabled = true;
        try {
            await apiRequest(`/api/meetings/${selectedMeeting.id}`, {
                method: "DELETE",
                headers: {
                    "X-User-Id": elements.userId ? elements.userId.value.trim() : selectedMeeting.organizerId
                }
            });
            selectedMeeting = { ...selectedMeeting, status: "CANCELLED" };
            meetings = meetings.map(item => item.id === selectedMeeting.id ? selectedMeeting : item);
            elements.dialog.close();
            renderRows();
            if (elements.editButton) elements.editButton.disabled = true;
            if (elements.cancelButton) elements.cancelButton.disabled = true;
            showMessage("Đã hủy cuộc họp thành công.", "success");
        } catch (error) {
            elements.dialog.close();
            showMessage(error.message, "error");
        } finally {
            elements.confirmCancelButton.disabled = false;
        }
    });
}

function renderSlots(slots) {
    const clockIcon = '<svg viewBox="0 0 24 24"><path d="M12 2a10 10 0 1 1 0 20 10 10 0 0 1 0-20Zm0 2a8 8 0 1 0 0 16 8 8 0 0 0 0-16Zm1 3v4.6l3.2 3.2-1.4 1.4L11 12.4V7h2Z"/></svg>';
    elements.slotList.innerHTML = slots.map(slot => `<div class="slot">${clockIcon}<span>${slot}</span></div>`).join("");
}

if (elements.findSlotsButton) {
    elements.findSlotsButton.addEventListener("click", () => {
        const names = elements.participants.value.split(",").map(name => name.trim()).filter(Boolean);
        if (!elements.suggestionDate.value || names.length === 0) {
            elements.slotList.innerHTML = '<p class="slot-empty">Hãy chọn ngày và nhập ít nhất một người tham dự.</p>';
            return;
        }

        const day = new Date(`${elements.suggestionDate.value}T00:00:00`).getDay();
        const weekdaySlots = ["09:00 - 10:00", "10:00 - 11:00", "15:00 - 16:00"];
        const weekendSlots = ["09:30 - 10:30", "14:00 - 15:00"];
        renderSlots(day === 0 || day === 6 ? weekendSlots : weekdaySlots);
    });
}

if (elements.suggestionDate) {
    elements.suggestionDate.value = new Date().toISOString().slice(0, 10);
}

loadMeetings();
