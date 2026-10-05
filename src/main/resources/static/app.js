const meetingList = document.querySelector('#meeting-list');
const dialog = document.querySelector('#meeting-dialog');
const form = document.querySelector('#meeting-form');
const tabs = document.querySelectorAll('.tab');
const fields = {
    id: document.querySelector('#meeting-id'),
    title: document.querySelector('#title'),
    description: document.querySelector('#description'),
    room: document.querySelector('#meeting-room'),
    start: document.querySelector('#start-time'),
    end: document.querySelector('#end-time'),
    participants: document.querySelector('#participants'),
    recurrence: document.querySelector('#recurrence'),
    repeatCount: document.querySelector('#repeat-count')
};

let meetings = [];
let equipment = [];
let equipmentStatuses = new Map();
let meetingEquipment = [];
let selectedMeetingEquipmentIds = new Set();
let meetingEquipmentRequestSequence = 0;
let currentView = 'meetings';
let toastTimer;

const formatDate = (value, options) => new Intl.DateTimeFormat('vi-VN', options).format(new Date(value));
const escapeHtml = (value = '') => String(value).replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
}[character]));

async function api(path, options = {}) {
    const response = await fetch(path, {
        ...options,
        headers: { 'Content-Type': 'application/json', ...options.headers }
    });
    if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        const fallback = response.status === 409
            ? 'Khung giờ này không còn đủ thiết bị khả dụng hoặc bị trùng lịch.'
            : response.status === 400
                ? 'Thông tin không hợp lệ, vui lòng kiểm tra lại.'
                : 'Không thể hoàn tất thao tác.';
        throw new Error(body.detail || body.message || fallback);
    }
    return response.status === 204 ? null : response.json();
}

async function loadMeetings() {
    try {
        meetings = await api(currentView === 'history' ? '/api/meetings/history' : '/api/meetings');
        render();
    } catch (error) {
        showToast(error.message);
    }
}

function setView(view) {
    currentView = view;
    tabs.forEach(tab => tab.classList.toggle('active', tab.dataset.view === view));
    document.querySelector('#meetings-view').hidden = view === 'equipment';
    document.querySelector('#equipment-view').hidden = view !== 'equipment';
    document.querySelector('#new-meeting').hidden = view === 'equipment';
    document.querySelector('#new-equipment').hidden = view !== 'equipment';
    if (view === 'equipment') {
        loadEquipment();
    } else {
        loadMeetings();
    }
}

async function loadEquipment() {
    try {
        equipment = await api('/api/equipment');
        const rooms = [...new Set(equipment.map(item => item.room))].sort((a, b) => a.localeCompare(b, 'vi'));
        const roomFilter = document.querySelector('#equipment-room-filter');
        const selectedRoom = roomFilter.value;
        roomFilter.innerHTML = '<option value="">Tất cả phòng</option>' + rooms.map(value => `<option value="${escapeHtml(value)}">${escapeHtml(value)}</option>`).join('');
        if (rooms.includes(selectedRoom)) roomFilter.value = selectedRoom;
        await refreshEquipmentStatuses();
    } catch (error) {
        showToast(error.message);
    }
}

async function refreshEquipmentStatuses() {
    const atInput = document.querySelector('#equipment-at');
    if (!atInput.value) atInput.value = toLocalInput(new Date());
    const at = new Date(atInput.value).toISOString();
    const rooms = [...new Set(equipment.map(item => item.room))];
    try {
        const results = await Promise.all(rooms.map(room => api(`/api/equipment/status?room=${encodeURIComponent(room)}&at=${encodeURIComponent(at)}`)));
        equipmentStatuses = new Map(results.flat().map(item => [item.equipmentId, item]));
        renderEquipment();
        updateSelectedAvailability();
    } catch (error) {
        showToast(error.message);
    }
}

function renderEquipment() {
    const list = document.querySelector('#equipment-list');
    const labels = { AVAILABLE: 'Có sẵn', BOOKED: 'Đã đặt', MAINTENANCE: 'Bảo trì' };
    const roomFilter = document.querySelector('#equipment-room-filter').value;
    const visibleEquipment = equipment.filter(item => !roomFilter || item.room === roomFilter);
    list.innerHTML = visibleEquipment.map(item => {
        const status = equipmentStatuses.get(item.id);
        const badges = (status?.statuses || []).map(value => `<span class="equipment-status ${value.toLowerCase()}">${labels[value]}</span>`).join('');
        return `<article class="equipment-row">
            <div class="equipment-main"><span class="equipment-icon" aria-hidden="true">▧</span><div><strong>${escapeHtml(item.name)}</strong><span>${escapeHtml(item.room)}</span></div></div>
            <div class="equipment-quantities"><span><strong>${status?.availableQuantity ?? item.totalQuantity}</strong> có sẵn</span><span>${status?.bookedQuantity ?? 0} đặt · ${status?.maintenanceQuantity ?? 0} bảo trì / ${item.totalQuantity}</span></div>
            <div class="equipment-statuses">${badges || '<span class="field-hint">Chưa có trạng thái</span>'}</div>
        </article>`;
    }).join('');
    document.querySelector('#equipment-count').textContent = `${visibleEquipment.length} thiết bị`;
    document.querySelector('#empty-equipment').hidden = visibleEquipment.length > 0;
}

function openEquipmentDialog() {
    document.querySelector('#equipment-form').reset();
    document.querySelector('#equipment-total').value = '1';
    document.querySelector('#equipment-error').textContent = '';
    document.querySelector('#equipment-dialog').showModal();
}

function render() {
    const today = new Date();
    const startOfToday = new Date(today.getFullYear(), today.getMonth(), today.getDate());
    const endOfToday = new Date(today.getFullYear(), today.getMonth(), today.getDate() + 1);
    const activeMeetings = meetings.filter(meeting => meeting.status === 'SCHEDULED');
    document.querySelector('#upcoming-count').textContent = activeMeetings.filter(meeting => new Date(meeting.endTime) >= today).length;
    document.querySelector('#today-count').textContent = activeMeetings.filter(meeting => {
        const start = new Date(meeting.startTime);
        return start >= startOfToday && start < endOfToday;
    }).length;
    document.querySelector('#cancelled-count').textContent = meetings.filter(meeting => meeting.status === 'CANCELLED').length;

    const search = document.querySelector('#search').value.trim().toLocaleLowerCase('vi');
    const status = document.querySelector('#status-filter').value;
    const visible = meetings.filter(meeting => {
        const matchesText = `${meeting.title} ${meeting.description || ''} ${(meeting.participants || []).join(' ')}`.toLocaleLowerCase('vi').includes(search);
        return matchesText && (status === 'ALL' || meeting.status === status);
    });

    meetingList.innerHTML = visible.map(meeting => {
        const cancelled = meeting.status === 'CANCELLED';
        const completed = new Date(meeting.endTime) < today && !cancelled;
        const statusLabel = cancelled ? 'Đã hủy' : completed ? 'Đã kết thúc' : 'Đã lên lịch';
        const statusClass = cancelled ? 'cancelled' : completed ? 'completed' : '';
        const dateLabel = formatDate(meeting.startTime, { weekday: 'short', day: '2-digit', month: '2-digit', year: 'numeric' });
        const startLabel = formatDate(meeting.startTime, { hour: '2-digit', minute: '2-digit' });
        const endLabel = formatDate(meeting.endTime, { hour: '2-digit', minute: '2-digit' });
        const participants = (meeting.participants || []).map(escapeHtml).join(', ') || 'Chưa có người tham dự';
        const actions = currentView === 'meetings' && !cancelled && !completed
            ? `<button class="text-action" data-edit="${meeting.id}">Sửa</button><button class="text-action delete" data-cancel="${meeting.id}">Hủy</button>`
            : '';
        const repeatMark = meeting.recurrenceSeriesId ? '<span title="Cuộc họp định kỳ"> · Lặp lại</span>' : '';
        return `<tr>
            <td><div class="meeting-name"><span class="meeting-symbol" aria-hidden="true">▦</span><span>${escapeHtml(meeting.title)}${repeatMark}</span></div>${meeting.description ? `<p class="meeting-description">${escapeHtml(meeting.description)}</p>` : ''}</td>
            <td>${dateLabel}<br>${startLabel} – ${endLabel}</td>
            <td><div class="participant-list" title="${participants}">${participants}</div></td>
            <td><span class="status ${statusClass}">${statusLabel}</span></td>
            <td><div class="row-actions">${actions}</div></td>
        </tr>`;
    }).join('');

    document.querySelector('#empty-state').hidden = visible.length > 0;
    document.querySelector('#result-count').textContent = `${visible.length} cuộc họp`;
    document.querySelector('#list-title').textContent = currentView === 'history' ? 'Cuộc họp đã qua' : 'Tất cả cuộc họp';
    document.querySelector('#page-title').textContent = currentView === 'history' ? 'Lịch sử' : 'Lịch họp';
    document.querySelector('#page-subtitle').textContent = currentView === 'history'
        ? 'Xem lại các cuộc họp đã diễn ra.'
        : 'Theo dõi và sắp xếp các cuộc họp của bạn.';
    document.querySelector('#status-filter').hidden = currentView === 'history';
}

function openCreate() {
    form.reset();
    selectedMeetingEquipmentIds.clear();
    fields.id.value = '';
    fields.repeatCount.value = '3';
    document.querySelector('#dialog-title').textContent = 'Tạo cuộc họp';
    document.querySelector('#save-meeting').textContent = 'Lưu cuộc họp';
    document.querySelector('#repeat-count-wrap').hidden = true;
    document.querySelector('#suggestions').replaceChildren();
    document.querySelector('#form-error').textContent = '';
    const start = new Date(Date.now() + 60 * 60 * 1000);
    start.setMinutes(Math.ceil(start.getMinutes() / 30) * 30, 0, 0);
    const end = new Date(start.getTime() + 60 * 60 * 1000);
    fields.start.value = toLocalInput(start);
    fields.end.value = toLocalInput(end);
    document.querySelector('#meeting-equipment-section').hidden = false;
    refreshMeetingEquipment();
    dialog.showModal();
}

function toLocalInput(date) {
    const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 16);
}

function openEdit(id) {
    const meeting = meetings.find(item => item.id === Number(id));
    if (!meeting) return;
    form.reset();
    fields.id.value = meeting.id;
    fields.title.value = meeting.title;
    fields.description.value = meeting.description || '';
    fields.room.value = meeting.room || '';
    fields.start.value = toLocalInput(new Date(meeting.startTime));
    fields.end.value = toLocalInput(new Date(meeting.endTime));
    fields.participants.value = (meeting.participants || []).join(', ');
    selectedMeetingEquipmentIds.clear();
    document.querySelector('#meeting-equipment-section').hidden = true;
    fields.recurrence.value = 'NONE';
    document.querySelector('#dialog-title').textContent = 'Chỉnh sửa cuộc họp';
    document.querySelector('#save-meeting').textContent = 'Lưu thay đổi';
    document.querySelector('#repeat-count-wrap').hidden = true;
    document.querySelector('#suggestions').replaceChildren();
    document.querySelector('#form-error').textContent = '';
    dialog.showModal();
}

function payload() {
    return {
        title: fields.title.value.trim(),
        description: fields.description.value.trim(),
        room: fields.room.value.trim(),
        startTime: new Date(fields.start.value).toISOString(),
        endTime: new Date(fields.end.value).toISOString(),
        organizerId: 1,
        participants: fields.participants.value.split(',').map(value => value.trim()).filter(Boolean),
        recurrence: fields.recurrence.value,
        repeatCount: fields.recurrence.value === 'NONE' ? 0 : Number(fields.repeatCount.value)
    };
}

function renderMeetingEquipment(message) {
    const options = document.querySelector('#meeting-equipment-options');
    const count = document.querySelector('#meeting-equipment-count');
    options.replaceChildren();
    if (message) {
        const hint = document.createElement('p');
        hint.className = 'field-hint';
        hint.textContent = message;
        options.append(hint);
        count.textContent = '';
        return;
    }

    if (!meetingEquipment.length) {
        const empty = document.createElement('p');
        empty.className = 'field-hint';
        empty.textContent = 'Phòng này chưa có thiết bị.';
        options.append(empty);
        count.textContent = '0 thiết bị';
        return;
    }

    const selectableIds = new Set(meetingEquipment
        .filter(item => item.availableQuantity > 0)
        .map(item => String(item.equipmentId)));
    selectedMeetingEquipmentIds.forEach(id => {
        if (!selectableIds.has(id)) selectedMeetingEquipmentIds.delete(id);
    });

    meetingEquipment.forEach(item => {
        const available = item.availableQuantity > 0;
        const label = document.createElement('label');
        label.className = `meeting-equipment-option${available ? '' : ' unavailable'}`;

        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.value = item.equipmentId;
        checkbox.checked = available && selectedMeetingEquipmentIds.has(String(item.equipmentId));
        checkbox.disabled = !available;
        checkbox.addEventListener('change', () => {
            const id = String(item.equipmentId);
            if (checkbox.checked) selectedMeetingEquipmentIds.add(id);
            else selectedMeetingEquipmentIds.delete(id);
        });

        const details = document.createElement('span');
        details.className = 'meeting-equipment-details';
        const name = document.createElement('strong');
        name.textContent = item.name;

        const quantities = document.createElement('span');
        quantities.className = 'meeting-equipment-quantities';
        quantities.textContent = `${item.availableQuantity}/${item.totalQuantity} có sẵn · ${item.bookedQuantity} đã đặt · ${item.maintenanceQuantity} bảo trì`;

        const status = document.createElement('span');
        status.className = `meeting-equipment-status ${available ? 'available' : 'unavailable'}`;
        status.textContent = available
            ? 'Có thể chọn'
            : item.maintenanceQuantity > 0 ? 'Bảo trì / hết hàng' : 'Đã đặt hết';

        details.append(name, quantities, status);
        label.append(checkbox, details);
        options.append(label);
    });
    count.textContent = `${meetingEquipment.length} thiết bị`;
}

async function refreshMeetingEquipment() {
    const sequence = ++meetingEquipmentRequestSequence;
    const room = fields.room.value.trim();
    if (!room) {
        meetingEquipment = [];
        renderMeetingEquipment('Chọn phòng để xem thiết bị khả dụng.');
        return;
    }

    if (fields.start.value && fields.end.value
            && new Date(fields.end.value) <= new Date(fields.start.value)) {
        meetingEquipment = [];
        renderMeetingEquipment('Thời gian kết thúc phải sau thời gian bắt đầu.');
        return;
    }

    renderMeetingEquipment('Đang tải trạng thái thiết bị…');
    const at = fields.start.value
        ? new Date(fields.start.value).toISOString()
        : new Date().toISOString();
    try {
        const result = await api(`/api/equipment/status?room=${encodeURIComponent(room)}&at=${encodeURIComponent(at)}`);
        if (sequence !== meetingEquipmentRequestSequence) return;
        meetingEquipment = result;
        renderMeetingEquipment();
    } catch (requestError) {
        if (sequence !== meetingEquipmentRequestSequence) return;
        meetingEquipment = [];
        renderMeetingEquipment(`Không tải được trạng thái thiết bị: ${requestError.message}`);
    }
}

async function bookSelectedEquipment(createdMeeting) {
    const selectedIds = [...selectedMeetingEquipmentIds];
    if (selectedIds.length === 0) return;

    let occurrences = [createdMeeting];
    const createdBookings = [];

    try {
        const allMeetings = await api('/api/meetings');
        const matchingMeetings = allMeetings.filter(meeting =>
            Number(meeting.id) === Number(createdMeeting.id)
            || Number(meeting.recurrenceSeriesId) === Number(createdMeeting.id)
        );
        if (matchingMeetings.length) occurrences = matchingMeetings;

        for (const meeting of occurrences) {
            for (const selectedId of selectedIds) {
                const booking = await api('/api/equipment/bookings', {
                    method: 'POST',
                    body: JSON.stringify({
                        equipmentId: Number(selectedId),
                        meetingId: meeting.id,
                        room: meeting.room,
                        quantity: 1,
                        startTime: meeting.startTime,
                        endTime: meeting.endTime
                    })
                });
                createdBookings.push(booking);
            }
        }
    } catch (bookingError) {
        const rollbackErrors = [];
        for (const booking of createdBookings.reverse()) {
            try {
                await api(`/api/equipment/bookings/${booking.id}`, { method: 'DELETE' });
            } catch (rollbackError) {
                rollbackErrors.push(rollbackError.message);
            }
        }
        for (const meeting of [...occurrences].reverse()) {
            try {
                await api(`/api/meetings/${meeting.id}`, { method: 'DELETE' });
            } catch (rollbackError) {
                rollbackErrors.push(rollbackError.message);
            }
        }
        const rollbackMessage = rollbackErrors.length
            ? ` Không thể hoàn tác đầy đủ: ${rollbackErrors.join('; ')}.`
            : ' Cuộc họp đã được hủy.';
        throw new Error(`Không thể đặt thiết bị: ${bookingError.message}.${rollbackMessage}`);
    }
}

form.addEventListener('submit', async event => {
    event.preventDefault();
    const error = document.querySelector('#form-error');
    error.textContent = '';
    if (new Date(fields.end.value) <= new Date(fields.start.value)) {
        error.textContent = 'Thời gian kết thúc phải sau thời gian bắt đầu.';
        return;
    }
    try {
        const id = fields.id.value;
        const meeting = await api(id ? `/api/meetings/${id}` : '/api/meetings', {
            method: id ? 'PUT' : 'POST',
            body: JSON.stringify(payload())
        });
        if (!id) await bookSelectedEquipment(meeting);
        dialog.close();
        showToast(id ? 'Đã cập nhật cuộc họp.' : 'Đã tạo cuộc họp.');
        await loadMeetings();
    } catch (requestError) {
        error.textContent = requestError.message;
    }
});

fields.room.addEventListener('input', () => {
    selectedMeetingEquipmentIds.clear();
    refreshMeetingEquipment();
});
fields.start.addEventListener('change', refreshMeetingEquipment);
fields.end.addEventListener('change', refreshMeetingEquipment);

document.querySelector('#find-times').addEventListener('click', async () => {
    const date = fields.start.value ? fields.start.value.slice(0, 10) : toLocalInput(new Date()).slice(0, 10);
    const duration = fields.start.value && fields.end.value
        ? Math.max(15, Math.round((new Date(fields.end.value) - new Date(fields.start.value)) / 60000))
        : 60;
    const people = fields.participants.value.split(',').map(value => value.trim()).filter(Boolean);
    const target = document.querySelector('#suggestions');
    target.textContent = 'Đang tìm...';
    try {
        const suggestions = await api('/api/meetings/suggestions', {
            method: 'POST',
            body: JSON.stringify({ date, durationMinutes: duration, participants: people })
        });
        target.innerHTML = suggestions.length
            ? suggestions.map((slot, index) => `<button type="button" class="suggestion-chip" data-slot="${index}">${formatDate(slot.startTime, { hour: '2-digit', minute: '2-digit' })} – ${formatDate(slot.endTime, { hour: '2-digit', minute: '2-digit' })}</button>`).join('')
            : '<span class="field-hint">Không tìm thấy khung giờ phù hợp.</span>';
        target.querySelectorAll('[data-slot]').forEach(button => button.addEventListener('click', () => {
            const slot = suggestions[Number(button.dataset.slot)];
            fields.start.value = toLocalInput(new Date(slot.startTime));
            fields.end.value = toLocalInput(new Date(slot.endTime));
            refreshMeetingEquipment();
        }));
    } catch (error) {
        target.textContent = error.message;
    }
});

document.querySelector('#recurrence').addEventListener('change', event => {
    document.querySelector('#repeat-count-wrap').hidden = event.target.value === 'NONE';
});
document.querySelector('#new-meeting').addEventListener('click', openCreate);
document.querySelector('#empty-create').addEventListener('click', openCreate);
document.querySelector('#close-dialog').addEventListener('click', () => dialog.close());
document.querySelector('#cancel-dialog').addEventListener('click', () => dialog.close());
document.querySelector('#search').addEventListener('input', render);
document.querySelector('#status-filter').addEventListener('change', render);

meetingList.addEventListener('click', async event => {
    const editButton = event.target.closest('[data-edit]');
    const cancelButton = event.target.closest('[data-cancel]');
    if (editButton) openEdit(editButton.dataset.edit);
    if (cancelButton && window.confirm('Bạn có chắc muốn hủy cuộc họp này?')) {
        try {
            await api(`/api/meetings/${cancelButton.dataset.cancel}`, { method: 'DELETE' });
            showToast('Đã hủy cuộc họp.');
            await loadMeetings();
        } catch (error) {
            showToast(error.message);
        }
    }
});

tabs.forEach(tab => tab.addEventListener('click', () => {
    setView(tab.dataset.view);
}));

document.querySelector('#new-equipment').addEventListener('click', openEquipmentDialog);
document.querySelector('#new-equipment-mobile').addEventListener('click', openEquipmentDialog);
document.querySelector('#close-equipment-dialog').addEventListener('click', () => document.querySelector('#equipment-dialog').close());
document.querySelector('#cancel-equipment-dialog').addEventListener('click', () => document.querySelector('#equipment-dialog').close());
document.querySelector('#equipment-room-filter').addEventListener('change', loadEquipment);
document.querySelector('#refresh-equipment').addEventListener('click', refreshEquipmentStatuses);
document.querySelector('#equipment-at').addEventListener('change', refreshEquipmentStatuses);
document.querySelector('#equipment-form').addEventListener('submit', async event => {
    event.preventDefault();
    const error = document.querySelector('#equipment-error');
    error.textContent = '';
    try {
        await api('/api/equipment', {
            method: 'POST',
            body: JSON.stringify({
                name: document.querySelector('#equipment-name').value.trim(),
                room: document.querySelector('#equipment-room').value.trim(),
                quantity: Number(document.querySelector('#equipment-total').value)
            })
        });
        document.querySelector('#equipment-dialog').close();
        showToast('Đã thêm thiết bị.');
        await loadEquipment();
    } catch (requestError) {
        error.textContent = requestError.message;
    }
});

function showToast(message) {
    const toast = document.querySelector('#toast');
    toast.textContent = message;
    toast.classList.add('visible');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove('visible'), 2600);
}

document.querySelector('#today-label').textContent = formatDate(new Date(), { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
document.querySelector('#equipment-at').value = toLocalInput(new Date());
const bookingStart = new Date(Date.now() + 60 * 60 * 1000);
bookingStart.setMinutes(Math.ceil(bookingStart.getMinutes() / 30) * 30, 0, 0);
document.querySelector('#booking-start').value = toLocalInput(bookingStart);
document.querySelector('#booking-end').value = toLocalInput(new Date(bookingStart.getTime() + 60 * 60 * 1000));
loadMeetings();