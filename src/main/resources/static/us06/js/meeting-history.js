"use strict";
(() => {
  const $ = (id) => document.getElementById(id),
    API = () =>
      "/api/users/" + encodeURIComponent($("history-user").value.trim()) + "/meeting-history";
  const PAGE_SIZE = 10,
    ZONE = "Asia/Ho_Chi_Minh";
  let meetings = [],
    filtered = [],
    page = 1,
    busy = false,
    previousFocus;
  const labels = {
    COMPLETED: "Đã hoàn thành",
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
  async function load() {
    if (busy) return;
    if (!$("history-user").value.trim()) {
      state("Nhập mã người dùng để xem lịch sử", "Không có dữ liệu mẫu thay thế.");
      return;
    }
    busy = true;
    $("refresh").disabled = true;
    $("retry").hidden = true;
    $("rows").replaceChildren();
    meetings = [];
    filtered = [];
    resetCounts();
    state("Đang tải lịch sử…", "", true);
    const controller = new AbortController(),
      timer = setTimeout(() => controller.abort(), 15000);
    try {
      const response = await fetch(API(), {
        headers: { Accept: "application/json" },
        credentials: "same-origin",
        signal: controller.signal,
      });
      if (!response.ok) {
        const messages = {
          400: "Yêu cầu tải lịch sử không hợp lệ.",
          401: "Vui lòng đăng nhập để xem lịch sử.",
          403: "Bạn không có quyền xem lịch sử.",
          404: "Không tìm thấy API lịch sử. Kiểm tra ứng dụng backend đang chạy.",
          500: "Máy chủ gặp lỗi. Vui lòng thử lại.",
        };
        throw Error(messages[response.status] || "API trả lỗi HTTP " + response.status + ".");
      }
      const data = await response.json();
      if (!Array.isArray(data)) throw Error("Dữ liệu API phải là danh sách cuộc họp.");
      meetings = data
        .filter((m) => m && m.id != null)
        .sort((a, b) => Date.parse(b.startTime) - Date.parse(a.startTime));
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
    } catch (e) {
      state(
        "Không tải được lịch sử",
        e.name === "AbortError"
          ? "Máy chủ chưa phản hồi. Vui lòng thử lại."
          : e instanceof TypeError
            ? "Không kết nối được backend. Mở trang qua ứng dụng Spring Boot trên cổng 8080."
            : e.message,
      );
      $("retry").hidden = false;
    } finally {
      clearTimeout(timer);
      busy = false;
      $("refresh").disabled = false;
    }
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
          if (item && typeof item === "object" && item.status) row.append(badge(item.status));
          section.append(row);
        }
      } else section.append(node("p", "API chưa cung cấp dữ liệu này."));
      body.append(section);
    }
    if (m.minutes || m.notes) {
      const section = node("section", null, "detail-card");
      section.id = "detail-minutes";
      section.append(node("h3", "Biên bản / Ghi chú"), node("p", m.minutes || m.notes));
      body.append(section);
    }

    if (!$("detail").open) $("detail").showModal();
    document.body.classList.add("detail-open");
  }
  let detailToken = 0,liveDetailId=null;
  async function openDetail(m) {
    liveDetailId=m.id;
    const token = ++detailToken;
    $("minutes").disabled = true;
    previousFocus = document.activeElement;
    $("detail-title").textContent = "Chi tiết cuộc họp";
    $("detail-body").replaceChildren(node("p", "Đang tải chi tiết…"));
    $("detail").showModal();
    document.body.classList.add("detail-open");
    try {
      const response = await fetch(API() + "/" + encodeURIComponent(m.id), {
        headers: { Accept: "application/json" },
        credentials: "same-origin",
      });
      if (!response.ok)
        throw Error(
          response.status === 404
            ? "Không tìm thấy cuộc họp trong lịch sử của người dùng."
            : "Không tải được chi tiết (HTTP " + response.status + ").",
        );
      const data = await response.json();
      if (token === detailToken) {
        renderDetail(data);
      }
    } catch (e) {
      if (token === detailToken)
        $("detail-body").replaceChildren(
          node("p", e instanceof TypeError ? "Không kết nối được backend." : e.message),
        );
    }
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
  $("identity").addEventListener("submit", (e) => {
    e.preventDefault();
    if (!busy) load();
  });
  window.addEventListener('ttcs-data-updated',event=>{event.preventDefault();const refresh=async()=>{if(busy){setTimeout(refresh,700);return;}await load();if($('detail').open&&liveDetailId){const current=meetings.find(m=>String(m.id)===String(liveDetailId));if(current)renderDetail(current);else close();}};refresh();});
  resetCounts();
  state('Nhập mã người dùng để xem lịch sử');if(window.TTCSDemo){$('history-user').value=window.TTCSDemo.state.actor.id;load();}
})();
