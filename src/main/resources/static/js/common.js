const participantList = document.querySelector('#participants');
const countLabel = document.querySelector('#selected-count');
const slots = document.querySelector('#slots');
const resultCount = document.querySelector('#result-count');
const dateInput = document.querySelector('#date');
let selectedIds = [];

const today = new Date();
dateInput.value = [
  today.getFullYear(),
  String(today.getMonth() + 1).padStart(2, '0'),
  String(today.getDate()).padStart(2, '0')
].join('-');

async function loadParticipants() {
  const data = await window.meetingApi.getParticipants();
  participantList.replaceChildren(...data.participants.map((person) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'person';
    button.dataset.id = person.id;
    button.append(document.createTextNode(`${person.name} `));
    const role = document.createElement('small');
    role.textContent = person.role;
    button.append(role);
    return button;
  }));
  const participantButtons = [...participantList.querySelectorAll('.person')];
  participantButtons.forEach((button) => button.addEventListener('click', () => {
    button.classList.toggle('selected');
    selectedIds = [...participantList.querySelectorAll('.person.selected')].map((item) => item.dataset.id);
    countLabel.textContent = `${selectedIds.length} đã chọn`;
  }));
  participantButtons.forEach((button) => button.classList.add('selected'));
  selectedIds = participantButtons.map((button) => button.dataset.id);
  countLabel.textContent = `${selectedIds.length} đã chọn`;
  document.querySelector('#availability-form').requestSubmit();
}

document.querySelector('#availability-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  if (!selectedIds.length) { slots.innerHTML = '<p class="error">Hãy chọn ít nhất một người tham dự.</p>'; return; }
  slots.innerHTML = '<p class="empty">Đang kiểm tra lịch...</p>';
  const form = new FormData(event.currentTarget);
  try {
    const data = await window.meetingApi.findAvailability({ date: form.get('date'), participantIds: selectedIds.map(Number), durationMinutes: Number(form.get('durationMinutes')) });
    resultCount.textContent = `${data.count} lựa chọn`;
    slots.innerHTML = data.slots.length ? data.slots.map((slot, index) => `<div class="slot" style="animation-delay:${index * 50}ms"><span>${slot.start} — ${slot.end}</span><b>● Mọi người đều rảnh</b></div>`).join('') : '<p class="empty">Không có khung giờ chung trong ngày này.</p>';
  } catch (error) {
    slots.innerHTML = '<p class="error">Không thể kết nối máy chủ.</p>';
  }
});

loadParticipants().catch(() => { slots.innerHTML = '<p class="error">Không thể kết nối máy chủ.</p>'; });
