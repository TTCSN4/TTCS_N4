const meetingList = document.querySelector('#meeting-list');
const dialog = document.querySelector('#meeting-dialog');
const form = document.querySelector('#meeting-form');
const tabs = document.querySelectorAll('.tab');
const fields = { id: document.querySelector('#meeting-id'), title: document.querySelector('#title'), description: document.querySelector('#description'), room: document.querySelector('#meeting-room'), start: document.querySelector('#start-time'), end: document.querySelector('#end-time'), participants: document.querySelector('#participants'), recurrence: document.querySelector('#recurrence'), repeatCount: document.querySelector('#repeat-count') };
let meetings = [], equipment = [], equipmentStatuses = new Map(), users = [], meetingEquipment = [];
let selectedMeetingEquipmentIds = new Set(), meetingEquipmentRequestSequence = 0, currentView = 'meetings', toastTimer;
const formatDate = (value, options) => new Intl.DateTimeFormat('vi-VN', options).format(new Date(value));
const escapeHtml = (value = '') => String(value).replace(/[&<>"']/g, character => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[character]));
const normalizeSearch = value => String(value || '').toLocaleLowerCase('vi').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd');
const participantLabel = id => { const user = users.find(item => String(item.id || item.userId) === String(id)); return user ? `${user.fullName || user.name || id} · ${user.email || ''}`.replace(/ · $/, '') : String(id); };
function renderParticipantPreview(participants = []) { const preview = document.querySelector('#participant-preview'); preview.replaceChildren(); if (!participants.length) { preview.hidden = true; return; } const heading = document.createElement('strong'); heading.textContent = `Đã chọn ${participants.length} người tham dự:`; preview.append(heading); participants.forEach(id => { const item = document.createElement('span'); item.className = 'participant-chip'; item.textContent = participantLabel(id); preview.append(item); }); preview.hidden = false; }
async function api(path, options = {}) { const response = await fetch(path, {...options, headers: {'Content-Type':'application/json', ...options.headers}}); if (!response.ok) { const body = await response.json().catch(() => ({})); const fallback = response.status === 409 ? 'Khung giờ này không còn đủ thiết bị khả dụng hoặc bị trùng lịch.' : response.status === 400 ? 'Thông tin không hợp lệ, vui lòng kiểm tra lại.' : 'Không thể hoàn tất thao tác.'; throw new Error(body.detail || body.message || fallback); } return response.status === 204 ? null : response.json(); }
async function loadMeetings() { try { const [meetingData, userData] = await Promise.all([api(currentView === 'history' ? '/api/meetings/history' : '/api/meetings'), api('/api/users').catch(() => [])]); meetings = meetingData; users = Array.isArray(userData) ? userData : userData.users || []; render(); } catch (error) { showToast(error.message); } }
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
        equipment = (await api('/api/equipment')).map(item => ({
            id: item.equipmentId,
            name: item.equipmentName,
            room: item.roomId,
            totalQuantity: item.totalQuantity
        }));
        const rooms = [...new Set(equipment.map(item => item.room))].sort((a, b) => a.localeCompare(b, 'vi'));
        const roomFilter = document.querySelector('#equipment-room-filter');
        const selectedRoom = roomFilter.value;
        roomFilter.innerHTML = '<option value="">Táº¥t cáº£ phÃ²ng</option>' + rooms.map(value => `<option value="${escapeHtml(value)}">${escapeHtml(value)}</option>`).join('');
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
    const labels = { AVAILABLE: 'CÃ³ sáºµn', BOOKED: 'ÄÃ£ Ä‘áº·t', MAINTENANCE: 'Báº£o trÃ¬' };
    const roomFilter = document.querySelector('#equipment-room-filter').value;
    const visibleEquipment = equipment.filter(item => !roomFilter || item.room === roomFilter);
    list.innerHTML = visibleEquipment.map(item => {
        const status = equipmentStatuses.get(item.id);
        const badges = (status?.statuses || []).map(value => `<span class="equipment-status ${value.toLowerCase()}">${labels[value]}</span>`).join('');
        return `<article class="equipment-row">
            <div class="equipment-main"><span class="equipment-icon" aria-hidden="true">â–§</span><div><strong>${escapeHtml(item.name)}</strong><span>${escapeHtml(item.room)}</span></div></div>
            <div class="equipment-quantities"><span><strong>${status?.availableQuantity ?? item.totalQuantity}</strong> cÃ³ sáºµn</span><span>${status?.bookedQuantity ?? 0} Ä‘áº·t Â· ${status?.maintenanceQuantity ?? 0} báº£o trÃ¬ / ${item.totalQuantity}</span></div>
            <div class="equipment-statuses">${badges || '<span class="field-hint">ChÆ°a cÃ³ tráº¡ng thÃ¡i</span>'}</div>
        </article>`;
    }).join('');
    document.querySelector('#equipment-count').textContent = `${visibleEquipment.length} thiáº¿t bá»‹`;
    document.querySelector('#empty-equipment').hidden = visibleEquipment.length > 0;
}

function openEquipmentDialog() {
    document.querySelector('#equipment-form').reset();
    document.querySelector('#equipment-total').value = '1';
    document.querySelector('#equipment-type').value = 'GENERAL';
    document.querySelector('#equipment-status').value = 'AVAILABLE';
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

    const search = normalizeSearch(document.querySelector('#search').value.trim());
    const status = document.querySelector('#status-filter').value;
    const visible = meetings.filter(meeting => {
        const participantText = (meeting.participants || []).map(id => `${id} ${participantLabel(id)}`).join(' ');
        const searchable = normalizeSearch(`${meeting.id || meeting.meetingId} ${meeting.title} ${meeting.description || ''} ${meeting.room || meeting.roomName || ''} ${meeting.organizerName || ''} ${participantText}`);
        return searchable.includes(search) && (status === 'ALL' || meeting.status === status);
    });

    meetingList.innerHTML = visible.map(meeting => {
        const cancelled = meeting.status === 'CANCELLED';
        const completed = new Date(meeting.endTime) < today && !cancelled;
        const statusLabel = cancelled ? 'Đã hủy' : completed ? 'Đã kết thúc' : 'Đã lên lịch';
        const statusClass = cancelled ? 'cancelled' : completed ? 'completed' : '';
        const dateLabel = formatDate(meeting.startTime, { weekday: 'short', day: '2-digit', month: '2-digit', year: 'numeric' });
        const startLabel = formatDate(meeting.startTime, { hour: '2-digit', minute: '2-digit' });
        const endLabel = formatDate(meeting.endTime, { hour: '2-digit', minute: '2-digit' });
        const participants = (meeting.participants || []).map(participantLabel).map(escapeHtml).join(', ') || 'Chưa có người tham dự';
        const actions = currentView === 'meetings' && !cancelled && !completed
            ? `<button class="text-action" data-edit="${meeting.id}">Sửa</button><button class="text-action delete" data-cancel="${meeting.id}">Hủy</button>`
            : '';
        const repeatMark = meeting.recurrenceSeriesId ? '<span title="Cuá»™c há»p Ä‘á»‹nh ká»³"> Â· Láº·p láº¡i</span>' : '';
        return `<tr>
            <td><div class="meeting-name"><span class="meeting-symbol" aria-hidden="true">â–¦</span><span>${escapeHtml(meeting.title)}${repeatMark}</span></div>${meeting.description ? `<p class="meeting-description">${escapeHtml(meeting.description)}</p>` : ''}</td>
            <td>${dateLabel}<br>${startLabel} â€“ ${endLabel}</td>
            <td><div class="participant-list" title="${participants}">${participants}</div></td>
            <td><span class="status ${statusClass}">${statusLabel}</span></td>
            <td><div class="row-actions">${actions}</div></td>
        </tr>`;
    }).join('');

    document.querySelector('#empty-state').hidden = visible.length > 0;
    document.querySelector('#result-count').textContent = `${visible.length} cuá»™c há»p`;
    document.querySelector('#list-title').textContent = currentView === 'history' ? 'Cuá»™c há»p Ä‘Ã£ qua' : 'Táº¥t cáº£ cuá»™c há»p';
    document.querySelector('#page-title').textContent = currentView === 'history' ? 'Lá»‹ch sá»­' : 'Lá»‹ch há»p';
    document.querySelector('#page-subtitle').textContent = currentView === 'history'
        ? 'Xem láº¡i cÃ¡c cuá»™c há»p Ä‘Ã£ diá»…n ra.'
        : 'Theo dÃµi vÃ  sáº¯p xáº¿p cÃ¡c cuá»™c há»p cá»§a báº¡n.';
    document.querySelector('#status-filter').hidden = currentView === 'history';
}

function openCreate() {
    window.location.href = 'create-meeting.html';
}

function toLocalInput(date) {
    const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 16);
}

function openEdit(id) {
    const meeting = meetings.find(item => String(item.id) === String(id));
    if (!meeting) return;
    form.reset();
    fields.id.value = meeting.id;
    fields.title.value = meeting.title;
    fields.description.value = meeting.description || '';
    fields.room.value = meeting.room || '';
    fields.start.value = toLocalInput(new Date(meeting.startTime));
    fields.end.value = toLocalInput(new Date(meeting.endTime));
    const participants = meeting.participants || [];
    fields.participants.value = participants.join(', ');
    renderParticipantPreview(participants);
    selectedMeetingEquipmentIds.clear();
    document.querySelector('#meeting-equipment-section').hidden = false;
    fields.recurrence.value = 'NONE';
    document.querySelector('#dialog-title').textContent = 'Chá»‰nh sá»­a cuá»™c há»p';
    document.querySelector('#save-meeting').textContent = 'LÆ°u thay Ä‘á»•i';
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
        organizerId: window.currentUser?.id || window.authUser?.id || undefined,
        participants: fields.participants.value.split(',').map(value => value.trim()).filter(Boolean),
        equipment: [...selectedMeetingEquipmentIds].map(equipmentId => ({ equipmentId, quantity: 1 })),
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
        empty.textContent = 'PhÃ²ng nÃ y chÆ°a cÃ³ thiáº¿t bá»‹.';
        options.append(empty);
        count.textContent = '0 thiáº¿t bá»‹';
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
        quantities.textContent = `${item.availableQuantity}/${item.totalQuantity} cÃ³ sáºµn Â· ${item.bookedQuantity} Ä‘Ã£ Ä‘áº·t Â· ${item.maintenanceQuantity} báº£o trÃ¬`;

        const status = document.createElement('span');
        status.className = `meeting-equipment-status ${available ? 'available' : 'unavailable'}`;
        status.textContent = available
            ? 'CÃ³ thá»ƒ chá»n'
            : item.maintenanceQuantity > 0 ? 'Báº£o trÃ¬ / háº¿t hÃ ng' : 'ÄÃ£ Ä‘áº·t háº¿t';

        details.append(name, quantities, status);
        label.append(checkbox, details);
        options.append(label);
    });
    count.textContent = `${meetingEquipment.length} thiáº¿t bá»‹`;
}

async function refreshMeetingEquipment() {
    const sequence = ++meetingEquipmentRequestSequence;
    const room = fields.room.value.trim();
    if (!room) {
        meetingEquipment = [];
        renderMeetingEquipment('Chá»n phÃ²ng Ä‘á»ƒ xem thiáº¿t bá»‹ kháº£ dá»¥ng.');
        return;
    }

    if (fields.start.value && fields.end.value
            && new Date(fields.end.value) <= new Date(fields.start.value)) {
        meetingEquipment = [];
        renderMeetingEquipment('Thá»i gian káº¿t thÃºc pháº£i sau thá»i gian báº¯t Ä‘áº§u.');
        return;
    }

    renderMeetingEquipment('Äang táº£i tráº¡ng thÃ¡i thiáº¿t bá»‹â€¦');
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
        renderMeetingEquipment(`KhÃ´ng táº£i Ä‘Æ°á»£c tráº¡ng thÃ¡i thiáº¿t bá»‹: ${requestError.message}`);
    }
}

form.addEventListener('submit', async event => {
    event.preventDefault();
    const error = document.querySelector('#form-error');
    error.textContent = '';
    if (new Date(fields.end.value) <= new Date(fields.start.value)) {
        error.textContent = 'Thá»i gian káº¿t thÃºc pháº£i sau thá»i gian báº¯t Ä‘áº§u.';
        return;
    }
    try {
        const id = fields.id.value;
        const meeting = await api(id ? `/api/meetings/${id}` : '/api/meetings', {
            method: id ? 'PUT' : 'POST',
            body: JSON.stringify(payload())
        });
        dialog.close();
        showToast(id ? 'ÄÃ£ cáº­p nháº­t cuá»™c há»p.' : 'ÄÃ£ táº¡o cuá»™c há»p.');
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
    target.textContent = 'Äang tÃ¬m...';
    try {
        const suggestions = await api('/api/meetings/suggestions', {
            method: 'POST',
            body: JSON.stringify({ date, durationMinutes: duration, participants: people })
        });
        target.innerHTML = suggestions.length
            ? suggestions.map((slot, index) => `<button type="button" class="suggestion-chip" data-slot="${index}">${formatDate(slot.startTime, { hour: '2-digit', minute: '2-digit' })} â€“ ${formatDate(slot.endTime, { hour: '2-digit', minute: '2-digit' })}</button>`).join('')
            : '<span class="field-hint">KhÃ´ng tÃ¬m tháº¥y khung giá» phÃ¹ há»£p.</span>';
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
document.querySelector('#participants').addEventListener('input', () => {
    const values = document.querySelector('#participants').value.split(',').map(value => value.trim()).filter(Boolean);
    renderParticipantPreview(values);
});

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
                equipmentName: document.querySelector('#equipment-name').value.trim(),
                roomId: document.querySelector('#equipment-room').value.trim(),
                totalQuantity: Number(document.querySelector('#equipment-total').value),
                type: document.querySelector('#equipment-type').value.trim(),
                status: document.querySelector('#equipment-status').value
            })
        });
        document.querySelector('#equipment-dialog').close();
        showToast('ÄÃ£ thÃªm thiáº¿t bá»‹.');
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
loadMeetings();
