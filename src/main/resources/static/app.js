const elements = {
    rows: document.querySelector("#meetingRows"),
    newTitle: document.querySelector("#newTitle"),
    userId: document.querySelector("#userId"),
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
    elements.message.textContent = text;
    elements.message.className = `message visible ${type}`;
}

function clearMessage() {
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
    return new Date(value).toLocaleDateString("vi-VN");
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
            <td>—</td>
            <td><span class="status ${statusClass(meeting.status)}">${statusLabel(meeting.status)}</span></td>
        </tr>`).join("");
}

function selectMeeting(id) {
    selectedMeeting = meetings.find(meeting => meeting.id === id) ?? null;
    if (!selectedMeeting) return;

    elements.newTitle.value = selectedMeeting.title;
    elements.userId.value = selectedMeeting.organizerId;
    const editable = isEditable(selectedMeeting);
    elements.editButton.disabled = !editable;
    elements.cancelButton.disabled = !editable;
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
    } catch (error) {
        elements.rows.innerHTML = '<tr><td colspan="6" class="empty">Không tải được dữ liệu</td></tr>';
        showMessage(error.message, "error");
    }
}

elements.rows.addEventListener("click", event => {
    const row = event.target.closest("tr[data-id]");
    if (row) selectMeeting(Number(row.dataset.id));
});

elements.editButton.addEventListener("click", async () => {
    clearMessage();
    const title = elements.newTitle.value.trim();
    if (!selectedMeeting || !title || !elements.userId.reportValidity()) {
        showMessage("Hãy chọn cuộc họp, nhập tên mới và mã nhân viên hợp lệ.", "error");
        return;
    }

    elements.editButton.disabled = true;
    try {
        const updated = await apiRequest(`/api/meetings/${selectedMeeting.id}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json", "X-User-Id": elements.userId.value },
            body: JSON.stringify({
                title,
                description: selectedMeeting.description,
                startTime: selectedMeeting.startTime,
                endTime: selectedMeeting.endTime
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

elements.cancelButton.addEventListener("click", () => {
    if (!selectedMeeting || !elements.userId.reportValidity()) return;
    elements.confirmText.textContent = `Bạn có chắc muốn hủy cuộc họp “${selectedMeeting.title}”?`;
    elements.dialog.showModal();
});

elements.closeDialogButton.addEventListener("click", () => elements.dialog.close());

elements.confirmCancelButton.addEventListener("click", async () => {
    elements.confirmCancelButton.disabled = true;
    try {
        await apiRequest(`/api/meetings/${selectedMeeting.id}`, {
            method: "DELETE",
            headers: { "X-User-Id": elements.userId.value }
        });
        selectedMeeting = { ...selectedMeeting, status: "CANCELLED" };
        meetings = meetings.map(item => item.id === selectedMeeting.id ? selectedMeeting : item);
        elements.dialog.close();
        renderRows();
        elements.editButton.disabled = true;
        elements.cancelButton.disabled = true;
        showMessage("Đã hủy cuộc họp thành công.", "success");
    } catch (error) {
        elements.dialog.close();
        showMessage(error.message, "error");
    } finally {
        elements.confirmCancelButton.disabled = false;
    }
});

function renderSlots(slots) {
    const clockIcon = '<svg viewBox="0 0 24 24"><path d="M12 2a10 10 0 1 1 0 20 10 10 0 0 1 0-20Zm0 2a8 8 0 1 0 0 16 8 8 0 0 0 0-16Zm1 3v4.6l3.2 3.2-1.4 1.4L11 12.4V7h2Z"/></svg>';
    elements.slotList.innerHTML = slots.map(slot => `<div class="slot">${clockIcon}<span>${slot}</span></div>`).join("");
}

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

elements.suggestionDate.value = new Date().toISOString().slice(0, 10);
loadMeetings();
