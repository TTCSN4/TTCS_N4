"use strict";
(() => {
  const $ = (id) => document.getElementById(id);
  const PAGE_SIZE = 10,
    ZONE = "Asia/Ho_Chi_Minh";
  let meetings = [],
    filtered = [],
    page = 1,
    busy = false,
    previousFocus;
  const labels = {
    COMPLETED: "Đã diễn ra",
    CANCELLED: "Đã hủy",
    SCHEDULED: "Đã lên lịch",
    IN_PROGRESS: "Đang diễn ra",
    PENDING: "Chờ duyệt",
  };
  function node(tag, text, cls) {
    const el = document.createElement(tag);
    if (text != null) el.textContent = text;
    if (cls) el.className = cls;
    return el;
  }
  const name = (x) =>
    x && typeof x === "object"
      ? x.fullName || x.name || x.email || x.id || "Chưa có dữ liệu"
      : x == null || x === ""
        ? "Chưa có dữ liệu"
        : String(x);
  const room = (m) => name(m.roomName || m.room);
  const organizer = (m) => name(m.organizerName || m.organizer || m.organizerId);
  const time = (x, clock = false) => {
    const d = new Date(x);
    if (!x || !Number.isFinite(+d)) return "—";
    return d.toLocaleString("vi-VN", {
      timeZone: ZONE,
      ...(clock
        ? { hour: "2-digit", minute: "2-digit" }
        : { day: "2-digit", month: "2-digit", year: "numeric" }),
    });
  };
  const dateKey = (x) => {
    const d = new Date(x);
    return x && Number.isFinite(+d)
      ? new Intl.DateTimeFormat("sv-SE", {
          timeZone: ZONE,
          year: "numeric",
          month: "2-digit",
          day: "2-digit",
        }).format(d)
      : "";
  };
  const normalize = (x) =>
    String(x || "")
      .toLocaleLowerCase("vi")
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/đ/g, "d");
  function badge(status) {
    const b = node("span", labels[status] || status || "Chưa xác định", "badge");
    b.dataset.status = status || "UNKNOWN";
    return b;
  }
  function state(title, message = "", loading = false) {
    $("table-wrap").hidden = true;
    $("state").hidden = false;
    $("state").replaceChildren();
    if (loading) $("state").append(node("span", null, "spinner"));
    $("state").append(node("strong", title));
    if (message) $("state").append(node("p", message));
  }
  function resetCounts() {
    ["total", "completed", "cancelled"].forEach((id) => ($(id).textContent = "—"));
    $("count").textContent = "0";
    $("page-info").textContent = "";
    $("page-number").textContent = "";
    $("previous").disabled = $("next").disabled = true;
  }
  // Dữ liệu mẫu để xem giao diện độc lập, không gọi backend.
  const demoTitles = ["Họp triển khai dự án Q4", "Họp báo cáo tuần", "Đánh giá kết quả dự án", "Họp với đối tác ABC", "Họp định kỳ phòng IT", "Họp kế hoạch tháng 9", "Họp đào tạo nhân viên", "Họp tổng kết quý III", "Họp với khách hàng XYZ", "Họp kiểm thử hệ thống"];
  const demoDates = ["2025-09-10", "2025-09-08", "2025-09-05", "2025-09-01", "2025-08-28", "2025-08-25", "2025-08-20", "2025-08-15", "2025-08-10", "2025-08-05"];
  const demoMeetings = Array.from({ length: 42 }, (_, index) => {
    const date = index < 10 ? demoDates[index] : new Date(Date.UTC(2025, 7, 4) - (index - 10) * 86400000).toISOString().slice(0, 10);
    const start = ["09:00", "14:00", "10:00", "13:30", "09:00", "15:00", "08:30", "14:00", "09:00", "13:00"][index % 10];
    const end = ["10:30", "15:00", "11:30", "15:00", "10:00", "16:30", "11:00", "16:00", "10:30", "15:00"][index % 10];
    return {
      id: "DEMO-" + (index + 1), title: demoTitles[index % 10],
      description: "Thảo luận kế hoạch triển khai dự án trong quý 4, phân công nhiệm vụ và thống nhất tiến độ thực hiện.",
      room: ["Phòng A1", "Phòng B2", "Phòng A2", "Phòng VIP", "Phòng B1"][index % 5],
      organizerName: ["Nguyễn Văn A", "Trần Thị B", "Lê Văn C", "Nguyễn Văn A", "Phạm Thị D"][index % 5],
      startTime: `${date}T${start}:00+07:00`, endTime: `${date}T${end}:00+07:00`,
      status: index % 10 === 6 ? "CANCELLED" : "COMPLETED",
      createdAt: "2025-09-05T14:20:00+07:00", updatedAt: "2025-09-08T16:30:00+07:00",
      participants: [{ fullName: "Nguyễn Văn A", role: "Trưởng phòng", status: "Đã tham gia" }, { fullName: "Trần Thị B", role: "Nhân viên", status: "Đã tham gia" }, { fullName: "Lê Văn C", role: "Nhân viên", status: "Đã tham gia" }, { fullName: "Phạm Thị D", role: "Nhân viên", status: "Vắng mặt" }],
      equipment: [{ name: "Máy chiếu", role: "Epson EB-E500", status: "Đã sử dụng" }, { name: "Bảng viết", role: "Bảng trắng di động", status: "Đã sử dụng" }, { name: "Họp trực tuyến", role: "Zoom Meeting", status: "Đã sử dụng" }],
      notes: "Biên bản mẫu: thống nhất kế hoạch và cập nhật tiến độ trong cuộc họp tiếp theo.",
    };
  });
  function load() {
    meetings = demoMeetings.map((meeting) => ({ ...meeting }));
    $("retry").hidden = true;
    $("total").textContent = meetings.length;
    $("completed").textContent = meetings.filter((m) => m.status === "COMPLETED").length;
    $("cancelled").textContent = meetings.filter((m) => m.status === "CANCELLED").length;
      const oldRoom = $("room").value,
        oldStatus = $("status").value;
      $("room").replaceChildren();
      $("status").replaceChildren();
      const allRoom = node("option", "Tất cả phòng");
      allRoom.value = "";
      $("room").append(allRoom);
      const allStatus = node("option", "Tất cả trạng thái");
      allStatus.value = "";
      $("status").append(allStatus);
      for (const value of [...new Set(meetings.map(room))].sort()) {
        const o = node("option", value);
        o.value = value;
        $("room").append(o);
      }
      for (const value of [...new Set(meetings.map((m) => m.status).filter(Boolean))]) {
        const o = node("option", labels[value] || value);
        o.value = value;
        $("status").append(o);
      }
      $("room").value = oldRoom;
      $("status").value = oldStatus;
      page = 1;
      apply();
  }
  function apply() {
    const from = $("from").value,
      to = $("to").value;
    if (from && to && from > to) {
      $("filter-error").textContent = "Ngày kết thúc phải từ ngày bắt đầu trở đi.";
      $("filter-error").hidden = false;
      return;
    }
    $("filter-error").hidden = true;
    const q = normalize($("keyword").value.trim());
    filtered = meetings.filter((m) => {
      const day = dateKey(m.startTime);
      return (
        (!q || normalize(m.title).includes(q)) &&
        (!from || day >= from) &&
        (!to || day <= to) &&
        (!$("room").value || room(m) === $("room").value) &&
        (!$("status").value || m.status === $("status").value)
      );
    });
    render();
  }
  function render() {
    const pages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
    page = Math.min(page, pages);
    const start = (page - 1) * PAGE_SIZE;
    $("rows").replaceChildren();
    $("count").textContent = filtered.length;
    if (!filtered.length)
      state(
        meetings.length ? "Không có kết quả phù hợp" : "Chưa có lịch sử cuộc họp",
        meetings.length
          ? "Thử đổi từ khóa hoặc đặt lại bộ lọc."
          : "Các cuộc họp thuộc phạm vi lịch sử sẽ xuất hiện tại đây.",
      );
    else {
      $("state").hidden = true;
      $("table-wrap").hidden = false;
      filtered.slice(start, start + PAGE_SIZE).forEach((m, index) => {
        const tr = node("tr");
        for (const text of [
          start + index + 1,
          m.title || "Chưa có tiêu đề",
          time(m.startTime),
          time(m.startTime, true) + " – " + time(m.endTime, true),
          room(m),
          organizer(m),
        ])
          tr.append(node("td", text));
        const status = node("td");
        status.append(badge(m.status));
        tr.append(status);
        const action = node("td"),
          button = node("button", "◉ Xem chi tiết");
        button.type = "button";
        button.addEventListener("click", () => openDetail(m));
        action.append(button);
        tr.append(action);
        $("rows").append(tr);
      });
    }
    $("page-info").textContent = filtered.length
      ? "Hiển thị " +
        (start + 1) +
        " – " +
        Math.min(start + PAGE_SIZE, filtered.length) +
        " trong " +
        filtered.length +
        " cuộc họp"
      : "0 cuộc họp";
    $("page-buttons").replaceChildren();
    const first = Math.max(1, Math.min(page - 2, pages - 4));
    for (let n = first; n <= Math.min(pages, first + 4); n++) {
      const button = node("button", n);
      button.type = "button";
      button.className = n === page ? "current" : "";
      if (n === page) button.setAttribute("aria-current", "page");
      button.addEventListener("click", () => {
        page = n;
        render();
      });
      $("page-buttons").append(button);
    }
    $("page-number").textContent = page + " / " + pages;
    $("previous").disabled = page <= 1;
    $("next").disabled = page >= pages;
  }
  function renderDetail(m) {
    $("minutes").disabled = !(m.minutes || m.notes);
    $("detail-title").textContent = m.title || "Chi tiết cuộc họp";
    const body = $("detail-body");
    body.replaceChildren();
    const card = node("section", null, "detail-card"),
      head = node("div", null, "detail-title-row");
    head.append(node("h3", m.title || "Chưa có tiêu đề"), badge(m.status));
    card.append(head, node("h4", "Mô tả"), node("p", m.description || m.notes || "Chưa có mô tả."));
    const dl = node("dl");
    for (const [label, value] of [
      ["Ngày họp", time(m.startTime)],
      ["Người tổ chức", organizer(m)],
      ["Thời gian", time(m.startTime, true) + " – " + time(m.endTime, true)],
      ["Thời gian tạo", time(m.createdAt) + " " + (m.createdAt ? time(m.createdAt, true) : "")],
      ["Phòng họp", room(m)],
      ["Cập nhật lần cuối", time(m.updatedAt) + " " + (m.updatedAt ? time(m.updatedAt, true) : "")],
    ]) {
      const cell = node("div");
      cell.append(node("dt", label), node("dd", value));
      dl.append(cell);
    }
    card.append(dl);
    body.append(card);
    for (const [title, values] of [
      ["Danh sách người tham gia", m.participants || m.attendees],
      ["Thiết bị sử dụng", m.equipment || m.devices],
    ]) {
      const section = node("section", null, "detail-card");
      section.append(node("h3", title + (Array.isArray(values) ? " (" + values.length + ")" : "")));
      if (Array.isArray(values) && values.length) {
        for (const item of values) {
          const row = node("div", null, "person-row");
          row.append(
            node("span", name(item).slice(0, 2).toUpperCase(), "person-avatar"),
            node("span", name(item)),
          );
          const info = row.lastChild;
          if (item?.role) info.append(node("small", item.role));
          if (item && typeof item === "object" && item.status) row.append(badge(item.status));
          section.append(row);
        }
      } else section.append(node("p", "Chưa có dữ liệu mẫu cho mục này."));
      body.append(section);
    }
    if (m.minutes || m.notes) {
      const section = node("section", null, "detail-card");
      section.id = "detail-minutes";
      section.append(node("h3", "Biên bản / Ghi chú"), node("p", m.minutes || m.notes));
      body.append(section);
    }

    if (!$("detail").open) {
      if (window.matchMedia("(min-width: 1400px)").matches) $("detail").show(); else $("detail").showModal();
    }
    document.body.classList.add("detail-open");
  }
  let detailToken = 0;
  function openDetail(m) {
    previousFocus = document.activeElement;
    renderDetail(m);
  }
  function close() {
    detailToken++;
    if ($("detail").open) $("detail").close();
    document.body.classList.remove("detail-open");
    previousFocus?.focus();
  }
  $("minutes").addEventListener("click", () =>
    document
      .getElementById("detail-minutes")
      ?.scrollIntoView({ behavior: "smooth", block: "start" }),
  );
  $("close").addEventListener("click", close);
  $("back").addEventListener("click", close);
  $("detail").addEventListener("close", () => document.body.classList.remove("detail-open"));
  $("detail").addEventListener("click", (event) => {
    if (event.target === $("detail")) {
      const box = $("detail").getBoundingClientRect();
      if (
        event.clientX < box.left ||
        event.clientX > box.right ||
        event.clientY < box.top ||
        event.clientY > box.bottom
      )
        close();
    }
  });
  $("filters").addEventListener("submit", (e) => {
    e.preventDefault();
    if (!busy) {
      page = 1;
      apply();
    }
  });
  $("reset").addEventListener("click", () => {
    if (busy) return;
    $("filters").reset();
    page = 1;
    apply();
  });
  $("previous").addEventListener("click", () => {
    page--;
    render();
  });
  $("next").addEventListener("click", () => {
    page++;
    render();
  });
  $("refresh").addEventListener("click", load);
  $("retry").addEventListener("click", load);

  resetCounts();
  load();
  if (window.matchMedia("(min-width: 1400px)").matches) openDetail(meetings[0]);
})();
