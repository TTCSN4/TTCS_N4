document.addEventListener("DOMContentLoaded", () => {
  const form = document.getElementById("add-participant-form");
  const tbody = document.getElementById("participant-tbody");
  const searchInput = document.getElementById("search-input");
  const meetingSelect = document.getElementById("meeting-select");
  const roleFilter = document.getElementById("role-filter");
  const submitButton = form.querySelector('button[type="submit"]');
  const cancelButton = form.querySelector('button[type="button"]');
  const formTitle = document.querySelector(".form-header h2");
  const formDescription = document.querySelector(".form-header p");
  const meetingIdFromUrl = new URLSearchParams(window.location.search).get("meetingId");
  let editingParticipantId = null;

  const colors = ["bg-blue", "bg-purple", "bg-orange", "bg-teal", "bg-pink", "bg-cyan", "bg-indigo", "bg-magenta"];

  function getRandomColorClass() {
    return colors[Math.floor(Math.random() * colors.length)];
  }

  function getInitials(name) {
    const parts = name.trim().split(" ");
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  async function request(url, options = {}) {
    const response = await fetch(url, {
      ...options,
      headers: { Accept: "application/json", ...options.headers }
    });
    if (response.status === 204) return null;

    const result = await response.json().catch(() => null);
    if (!response.ok) {
      const message = result?.message || result?.error || `Yêu cầu thất bại (HTTP ${response.status}).`;
      throw new Error(message);
    }
    return result;
  }

  function applyFilters() {
    const keyword = searchInput.value.trim().toLocaleLowerCase("vi");
    const role = roleFilter.value;
    tbody.querySelectorAll("tr").forEach(row => {
      const matchesSearch = row.textContent.toLocaleLowerCase("vi").includes(keyword);
      const matchesRole = !role || row.dataset.role === role;
      row.hidden = !matchesSearch || !matchesRole;
    });
  }

  function renderParticipants(participants) {
    tbody.replaceChildren();
    participants.forEach((participant, index) => {
      const row = document.createElement("tr");
      row.dataset.id = participant.id;
      row.dataset.role = participant.role || "Tham dự";

      const numberCell = document.createElement("td");
      numberCell.textContent = String(index + 1);

      const nameCell = document.createElement("td");
      const userCell = document.createElement("div");
      userCell.className = "user-cell";
      const avatar = document.createElement("span");
      avatar.className = `avatar ${getRandomColorClass()}`;
      avatar.textContent = getInitials(participant.name || "");
      userCell.append(avatar, document.createTextNode(` ${participant.name || ""}`));
      nameCell.append(userCell);

      const emailCell = document.createElement("td");
      emailCell.className = "email-text";
      emailCell.textContent = participant.email || "";

      const roleCell = document.createElement("td");
      const roleBadge = document.createElement("span");
      roleBadge.className = `badge ${participant.role === "Khách mời" ? "badge-guest" : "badge-role"}`;
      roleBadge.textContent = participant.role || "Tham dự";
      roleCell.append(roleBadge);

      const statusCell = document.createElement("td");
      const statusBadge = document.createElement("span");
      statusBadge.className = `badge ${participant.status === "Đã tham gia" ? "badge-success" : "badge-warning"}`;
      statusBadge.textContent = participant.status || "Chưa tham gia";
      statusCell.append(statusBadge);

      const actionsCell = document.createElement("td");
      actionsCell.className = "actions";
      if (meetingSelect.value) {
        const editButton = document.createElement("button");
        editButton.type = "button";
        editButton.className = "btn-edit";
        editButton.dataset.action = "edit";
        editButton.title = "Sửa người tham gia";
        editButton.setAttribute("aria-label", "Sửa người tham gia");
        editButton.innerHTML = '<i class="fa-regular fa-pen-to-square"></i>';

        const deleteButton = document.createElement("button");
        deleteButton.type = "button";
        deleteButton.className = "btn-delete";
        deleteButton.dataset.action = "delete";
        deleteButton.title = "Xóa người tham gia";
        deleteButton.setAttribute("aria-label", "Xóa người tham gia");
        deleteButton.innerHTML = '<i class="fa-regular fa-trash-can"></i>';
        actionsCell.append(editButton, deleteButton);
      }

      row.append(numberCell, nameCell, emailCell, roleCell, statusCell, actionsCell);
      tbody.append(row);
    });
    applyFilters();
  }

  async function loadParticipants() {
    tbody.replaceChildren();
    const meetingId = meetingSelect.value;
    const participants = await request(meetingId
      ? `/api/meetings/${encodeURIComponent(meetingId)}/participants`
      : "/api/participants");
    renderParticipants(Array.isArray(participants) ? participants : []);
    updateFormAvailability();
  }

  function updateFormAvailability() {
    const hasMeeting = Boolean(meetingSelect.value);
    submitButton.disabled = false;
    document.querySelector(".form-header p").textContent = hasMeeting
      ? "Nhập thông tin để thêm người tham gia cuộc họp"
      : "Nhập thông tin, sau đó chọn cuộc họp để lưu người tham gia";
  }

  function resetForm() {
    form.reset();
    editingParticipantId = null;
    formTitle.textContent = "Thêm người tham gia";
    formDescription.textContent = meetingSelect.value
      ? "Nhập thông tin để thêm người tham gia cuộc họp"
      : "Chọn cuộc họp để thêm hoặc chỉnh sửa người tham gia";
    submitButton.textContent = "Lưu";
    cancelButton.hidden = true;
    updateFormAvailability();
  }

  meetingSelect.addEventListener("change", async () => {
    const url = new URL(window.location.href);
    if (meetingSelect.value) url.searchParams.set("meetingId", meetingSelect.value);
    else url.searchParams.delete("meetingId");
    window.history.replaceState({}, "", url);
    if (editingParticipantId !== null) resetForm();
    else updateFormAvailability();
    try {
      await loadParticipants();
    } catch (error) {
      alert(error.message);
    }
  });

  form.addEventListener("submit", async event => {
    event.preventDefault();
    const meetingId = meetingSelect.value;
    if (!meetingId) {
      alert("Vui lòng chọn cuộc họp trước khi lưu người tham gia.");
      meetingSelect.focus();
      return;
    }

    const payload = {
      name: document.getElementById("fullname").value.trim(),
      email: document.getElementById("email").value.trim(),
      role: document.getElementById("role").value,
      status: document.getElementById("status").value,
      notes: document.getElementById("note").value.trim()
    };
    const url = `/api/meetings/${encodeURIComponent(meetingId)}/participants`;
    const isEditing = editingParticipantId !== null;
    submitButton.disabled = true;
    try {
      await request(isEditing ? `${url}/${editingParticipantId}` : url, {
        method: isEditing ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });
      resetForm();
      await loadParticipants();
    } catch (error) {
      alert(error.message);
    } finally {
      updateFormAvailability();
    }
  });

  cancelButton.addEventListener("click", resetForm);
  document.getElementById("focus-participant-form").addEventListener("click", () => {
    if (!meetingSelect.value) {
      alert("Vui lòng chọn cuộc họp trước khi thêm người tham gia.");
      meetingSelect.focus();
      return;
    }
    document.getElementById("fullname").focus();
  });
  searchInput.addEventListener("input", applyFilters);
  roleFilter.addEventListener("change", applyFilters);

  tbody.addEventListener("click", async event => {
    const button = event.target.closest("button[data-action]");
    if (!button) return;

    const row = button.closest("tr");
    const participantId = row.dataset.id;
    const meetingId = meetingSelect.value;
    const url = `/api/meetings/${encodeURIComponent(meetingId)}/participants/${participantId}`;

    if (button.dataset.action === "delete") {
      if (!confirm("Bạn có chắc chắn muốn xóa người tham gia này?")) return;
      try {
        await request(url, { method: "DELETE" });
        await loadParticipants();
      } catch (error) {
        alert(error.message);
      }
      return;
    }

    try {
      const participants = await request(`/api/meetings/${encodeURIComponent(meetingId)}/participants`);
      const participant = participants.find(item => String(item.id) === participantId);
      if (!participant) throw new Error("Không tìm thấy người tham gia.");
      editingParticipantId = participant.id;
      document.getElementById("fullname").value = participant.name || "";
      document.getElementById("email").value = participant.email || "";
      document.getElementById("role").value = participant.role || "Tham dự";
      document.getElementById("status").value = participant.status || "Chưa tham gia";
      document.getElementById("note").value = participant.notes || "";
      formTitle.textContent = "Chỉnh sửa người tham gia";
      formDescription.textContent = "Cập nhật thông tin người tham gia cuộc họp";
      submitButton.textContent = "Cập nhật";
      cancelButton.hidden = false;
      document.getElementById("fullname").focus();
    } catch (error) {
      alert(error.message);
    }
  });

  async function initialize() {
    try {
      const meetings = await request("/api/meetings");
      meetings.forEach(meeting => {
        const option = document.createElement("option");
        option.value = meeting.id;
        option.textContent = meeting.title || `Cuộc họp ${meeting.id}`;
        meetingSelect.append(option);
      });
      if (meetingIdFromUrl && [...meetingSelect.options].some(option => option.value === meetingIdFromUrl)) {
        meetingSelect.value = meetingIdFromUrl;
      }
      await loadParticipants();
    } catch (error) {
      tbody.replaceChildren();
      const row = document.createElement("tr");
      const cell = document.createElement("td");
      cell.colSpan = 6;
      cell.textContent = error.message || "Không thể tải dữ liệu từ API.";
      row.append(cell);
      tbody.append(row);
      updateFormAvailability();
    }
  }

  cancelButton.hidden = true;
  initialize();
});