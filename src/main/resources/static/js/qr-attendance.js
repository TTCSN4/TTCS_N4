(() => {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const meetings = { M01: { title: "Họp triển khai dự án", room: "Phòng A1" }, M02: { title: "Họp báo cáo tuần", room: "Phòng B2" }, M03: { title: "Đánh giá tiến độ", room: "Phòng C1" } };
  const attendance = new Map();
  const history = [];
  let stream = null, cameraVersion = 0, scanTimer = null;
  const mode = () => document.querySelector('input[name="mode"]:checked').value;
  const dateText = (value) => new Intl.DateTimeFormat("vi-VN", { timeZone: "Asia/Ho_Chi_Minh", dateStyle: "short", timeStyle: "medium" }).format(value);
  function node(tag, text, cls) { const el = document.createElement(tag); if (text != null) el.textContent = text; if (cls) el.className = cls; return el; }
  function result(title, message, error = false) {
    $("result").className = error ? "error-result" : "";
    $("result").replaceChildren(node("div", error ? "!" : "✓", "result-icon"), node("strong", title), node("p", message));
  }
  function render() {
    const id = $("meeting").value, meeting = meetings[id], record = attendance.get(id);
    $("meetingTitle").textContent = meeting.title;
    $("room").textContent = meeting.room;
    $("sampleCode").textContent = "US27:" + id;
    $("attendanceBadge").textContent = record?.out ? "Đã check-out · mẫu" : record?.in ? "Đã check-in · mẫu" : "Chưa check-in";
    $("attendanceBadge").className = "badge " + (record?.out ? "out" : record?.in ? "in" : "");
    $("confirm").textContent = mode() === "in" ? "Xác nhận check-in" : "Xác nhận check-out";
    $("inCount").textContent = [...attendance.values()].filter(item => item.in).length;
    $("outCount").textContent = [...attendance.values()].filter(item => item.out).length;
    $("history").replaceChildren();
    const filtered = history.filter(item => $("filter").value === "all" || item.action === $("filter").value);
    for (const item of filtered) {
      const row = node("tr");
      for (const text of [meetings[item.id].title, meetings[item.id].room, item.action === "in" ? "Check-in" : "Check-out", dateText(item.time), "Thành công (mô phỏng)"]) row.append(node("td", text));
      $("history").append(row);
    }
    if (!filtered.length) { const row = node("tr"), cell = node("td", "Chưa có lịch sử điểm danh phù hợp.", "empty"); cell.colSpan = 5; row.append(cell); $("history").append(row); }
  }
  function confirmCode(value) {
    const code = value.trim(), id = $("meeting").value, action = mode(), record = attendance.get(id) || {};
    let error = "";
    if (!code) error = "Vui lòng nhập hoặc quét mã QR.";
    else if (code !== "US27:" + id) error = "Mã không hợp lệ hoặc không thuộc cuộc họp đã chọn.";
    else if (action === "in" && record.in) error = "Bạn đã check-in cuộc họp này.";
    else if (action === "out" && !record.in) error = "Bạn cần check-in trước khi check-out.";
    else if (action === "out" && record.out) error = "Bạn đã check-out cuộc họp này.";
    $("codeError").textContent = error;
    $("code").setAttribute("aria-invalid", String(Boolean(error)));
    if (error) { result("Không thể điểm danh", error, true); return; }
    const time = new Date();
    record[action] = time;
    attendance.set(id, record);
    history.unshift({ id, action, time });
    render();
    result(action === "in" ? "Check-in thành công (mô phỏng)" : "Check-out thành công (mô phỏng)", meetings[id].title + " · " + dateText(time));
    $("code").value = "";
  }
  $("codeForm").addEventListener("submit", (event) => { event.preventDefault(); confirmCode($("code").value); });
  $("meeting").addEventListener("change", () => { stopCamera(); $("code").value = ""; $("codeError").textContent = ""; render(); $("result").className = ""; $("result").replaceChildren(node("div", "○", "result-icon"), node("strong", "Chưa có thao tác"), node("p", "Quét hoặc nhập mã cho cuộc họp vừa chọn.")); });
  document.querySelectorAll('[name="mode"]').forEach(input => input.addEventListener("change", () => { stopCamera(); $("codeError").textContent = ""; render(); }));
  $("filter").addEventListener("change", render);
  function stopCamera() {
    cameraVersion++;
    clearTimeout(scanTimer);
    stream?.getTracks().forEach(track => track.stop());
    stream = null;
    $("video").srcObject = null;
    $("video").hidden = true;
    $("cameraPlaceholder").hidden = false;
    $("startCamera").disabled = false;
    $("stopCamera").disabled = true;
    $("cameraStatus").textContent = "Camera đã dừng. Có thể nhập mã thủ công.";
  }
  async function startCamera() {
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) { $("cameraStatus").textContent = "Camera cần HTTPS hoặc localhost. Vui lòng nhập mã thủ công."; return; }
    if (!window.BarcodeDetector) { $("cameraStatus").textContent = "Trình duyệt chưa hỗ trợ đọc QR. Vui lòng nhập mã thủ công."; return; }
    const version = ++cameraVersion;
    $("startCamera").disabled = true;
    $("stopCamera").disabled = false;
    $("cameraStatus").textContent = "Đang mở camera…";
    try {
      const detector = new BarcodeDetector({ formats: ["qr_code"] });
      const opened = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" }, audio: false });
      if (version !== cameraVersion) { opened.getTracks().forEach(track => track.stop()); return; }
      stream = opened;
      $("video").srcObject = stream;
      $("video").hidden = false;
      $("cameraPlaceholder").hidden = true;
      await $("video").play();
      if (version !== cameraVersion) return;
      $("cameraStatus").textContent = "Đang quét QR. Nội dung hợp lệ: " + $("sampleCode").textContent;
      async function scan() {
        if (version !== cameraVersion || !stream) return;
        try {
          const codes = await detector.detect($("video"));
          if (version !== cameraVersion) return;
          if (codes.length) { const value = codes[0].rawValue; stopCamera(); $("code").value = value; confirmCode(value); return; }
        } catch (_) { /* Chờ khung hình tiếp theo. */ }
        scanTimer = setTimeout(scan, 250);
      }
      scan();
    } catch (error) {
      if (version !== cameraVersion) return;
      stopCamera();
      $("cameraStatus").textContent = error.name === "NotAllowedError" ? "Quyền camera bị từ chối. Vui lòng nhập mã thủ công." : "Không mở được camera. Vui lòng nhập mã thủ công.";
    }
  }
  $("startCamera").addEventListener("click", startCamera);
  $("stopCamera").addEventListener("click", stopCamera);
  window.addEventListener("pagehide", stopCamera);
  document.addEventListener("visibilitychange", () => { if (document.hidden) stopCamera(); });
  $("menu").addEventListener("click", () => { const mobile = matchMedia("(max-width:700px)").matches; document.body.classList.toggle(mobile ? "menu-open" : "menu-closed"); });
  render();
})();
