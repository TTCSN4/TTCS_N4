"use strict";
(() => {
  const $ = (id) => document.getElementById(id),
    DAY = 86400000,
    colors = ["#3a87ff", "#32baa1", "#ffb51b", "#aab4a3", "#b262d5"];
  const rooms = [
    { id: "alpha", name: "Phòng Alpha", capacity: 20, status: "ACTIVE" },
    { id: "beta", name: "Phòng Beta", capacity: 15, status: "ACTIVE" },
    { id: "gamma", name: "Phòng Gamma", capacity: 10, status: "ACTIVE" },
    { id: "delta", name: "Phòng Delta", capacity: 8, status: "MAINTENANCE" },
    { id: "epsilon", name: "Phòng Epsilon", capacity: 6, status: "ACTIVE" },
  ];
  const events = [];
  for (let day = Date.UTC(2025, 0, 1); day <= Date.UTC(2026, 9, 31); day += DAY) {
    const date = new Date(day),
      weekday = date.getUTCDay();
    if (!weekday || weekday === 6) continue;
    rooms.forEach((room, index) => {
      const count = (date.getUTCDate() + index * 2) % 4;
      for (let n = 0; n < count; n++)
        events.push({
          roomId: room.id,
          date: date.toISOString().slice(0, 10),
          hours: 1 + ((index + n + date.getUTCDate()) % 4) * 0.5,
        });
    });
  }
  let report,
    descending = true,
    busy = false;
  const num = (n) => Number(n).toLocaleString("vi-VN", { maximumFractionDigits: 1 });
  const node = (tag, text, cls) => {
    const el = document.createElement(tag);
    if (text != null) el.textContent = text;
    if (cls) el.className = cls;
    return el;
  };
  const timestamp = (d) => Date.parse(d + "T00:00:00Z");
  const key = (d) => new Date(d).toISOString().slice(0, 10);
  function summarize(from, to, room, status) {
    const filtered = rooms.filter(
        (r) => (!room || r.id === room) && (!status || r.status === status),
      ),
      start = timestamp(from),
      end = timestamp(to);
    let working = 0;
    for (let d = start; d <= end; d += DAY) {
      const day = new Date(d).getUTCDay();
      if (day > 0 && day < 6) working++;
    }
    const selected = events.filter((e) => e.date >= from && e.date <= to),
      rows = filtered.map((r) => {
        const items = selected.filter((e) => e.roomId === r.id),
          hours = items.reduce((s, e) => s + e.hours, 0);
        return {
          ...r,
          count: items.length,
          hours,
          utilization: working ? (hours / (working * 8)) * 100 : 0,
        };
      });
    const ids = new Set(filtered.map((r) => r.id)),
      chosen = selected.filter((e) => ids.has(e.roomId)),
      hours = rows.reduce((s, r) => s + r.hours, 0);
    const trend = [];
    const days = Math.round((end - start) / DAY) + 1,
      step = Math.max(1, Math.ceil(days / 24));
    for (let d = start; d <= end; d += DAY * step) {
      const a = key(d),
        b = key(Math.min(end, d + DAY * (step - 1))),
        items = chosen.filter((e) => e.date >= a && e.date <= b);
      trend.push({
        date: a,
        end: b,
        count: items.length,
        hours: items.reduce((s, e) => s + e.hours, 0),
      });
    }
    return {
      rows,
      trend,
      bookings: chosen.length,
      hours,
      utilization: working && rows.length ? (hours / (working * 8 * rows.length)) * 100 : 0,
      from,
      to,
    };
  }
  function growth(id, value, previous) {
    const el = $(id + "Trend");
    if (!previous) {
      el.textContent = value ? "Chưa có dữ liệu kỳ trước" : "Không có thay đổi";
      el.dataset.direction = "flat";
      return;
    }
    const change = ((value - previous) / previous) * 100;
    el.textContent = (change >= 0 ? "↑ " : "↓ ") + num(Math.abs(change)) + "% so với kỳ trước";
    el.dataset.direction = change >= 0 ? "up" : "down";
  }
  function render() {
    $("roomTotal").textContent = report.rows.length;
    $("bookingTotal").textContent = num(report.bookings);
    $("hoursTotal").textContent = num(report.hours);
    $("usageTotal").textContent = num(report.utilization);
    const days = Math.round((timestamp(report.to) - timestamp(report.from)) / DAY) + 1,
      previous = summarize(
        key(timestamp(report.from) - DAY * days),
        key(timestamp(report.from) - DAY),
        $("roomFilter").value,
        $("statusFilter").value,
      );
    growth("roomTotal", report.rows.length, previous.rows.length);
    growth("bookingTotal", report.bookings, previous.bookings);
    growth("hoursTotal", report.hours, previous.hours);
    growth("usageTotal", report.utilization, previous.utilization);
    renderTrend();
    renderDonut();
    renderTop();
    renderTable();
  }
  function svg(tag, attrs = {}, text) {
    const el = document.createElementNS("http://www.w3.org/2000/svg", tag);
    Object.entries(attrs).forEach(([k, v]) => el.setAttribute(k, v));
    if (text != null) el.textContent = text;
    return el;
  }
  function renderTrend() {
    $("trendChart").replaceChildren();
    const width = 520,
      height = 200,
      left = 42,
      top = 14,
      bottom = 165,
      right = 478,
      maxCount = Math.max(5, Math.ceil(Math.max(0, ...report.trend.map((d) => d.count)) / 5) * 5),
      maxHours = Math.max(5, Math.ceil(Math.max(0, ...report.trend.map((d) => d.hours)) / 5) * 5);
    const chart = svg("svg", { viewBox: "0 0 " + width + " " + height, role: "img" });
    chart.append(
      svg("title", {}, "Lượt đặt và số giờ sử dụng trong kỳ " + report.from + " đến " + report.to),
    );
    for (let i = 0; i <= 4; i++) {
      const y = top + ((bottom - top) * i) / 4;
      chart.append(
        svg("line", { x1: left, y1: y, x2: right, y2: y, stroke: "#e4ecf7" }),
        svg("text", { x: left - 9, y: y + 4, "text-anchor": "end" }, num(maxCount * (1 - i / 4))),
        svg("text", { x: right + 9, y: y + 4 }, num(maxHours * (1 - i / 4))),
      );
    }
    const definitions = svg("defs"), gradient = svg("linearGradient", {
      id: "usageBarGradient", x1: "0", y1: "0", x2: "0", y2: "1"
    });
    gradient.append(svg("stop", { offset: "0%", "stop-color": "#60aaff" }),
      svg("stop", { offset: "100%", "stop-color": "#257aff" }));
    definitions.append(gradient);
    chart.append(definitions);
    const step = (right - left) / Math.max(1, report.trend.length),
      points = [];
    report.trend.forEach((d, index) => {
      const x = left + step * (index + 0.5),
        barHeight = (d.count / maxCount) * (bottom - top),
        bar = svg("rect", {
          x: x - step * 0.34,
          y: bottom - barHeight,
          width: step * 0.68,
          height: barHeight,
          rx: 1,
          fill: "url(#usageBarGradient)",
        });
      bar.append(
        svg(
          "title",
          {},
          d.date + " → " + d.end + ": " + d.count + " lượt, " + num(d.hours) + " giờ",
        ),
      );
      chart.append(bar);
      const y = bottom - (d.hours / maxHours) * (bottom - top);
      points.push([x, y]);
      if (
        index % Math.max(1, Math.floor(report.trend.length / 6)) === 0 ||
        index === report.trend.length - 1
      )
        chart.append(
          svg(
            "text",
            { x, y: 188, "text-anchor": "middle" },
            d.date.slice(8) + "/" + d.date.slice(5, 7),
          ),
        );
    });
    chart.append(
      svg("polyline", {
        points: points.map((p) => p.join(",")).join(" "),
        fill: "none",
        stroke: "#15b86d",
        "stroke-width": 2.4,
      }),
    );
    points.forEach(([x, y], i) => {
      const circle = svg("circle", {
        cx: x,
        cy: y,
        r: 2.6,
        fill: "white",
        stroke: "#15b86d",
        "stroke-width": 1.5,
      });
      circle.append(
        svg("title", {}, report.trend[i].date + ": " + num(report.trend[i].hours) + " giờ"),
      );
      chart.append(circle);
    });
    $("trendChart").append(chart);
  }
  function renderDonut() {
    let start = 0;
    const segments = report.rows.map((r) => {
      const end = start + (report.hours ? (r.hours / report.hours) * 100 : 0),
        color = colors[rooms.findIndex((room) => room.id === r.id)];
      const s = color + " " + start + "% " + end + "%";
      start = end;
      return s;
    });
    $("donut").style.background = report.hours
      ? "conic-gradient(" + segments.join(",") + ")"
      : "#e6edf7";
    $("donut").setAttribute("aria-label", "Tổng " + num(report.hours) + " giờ sử dụng");
    $("donutTotal").textContent = num(report.hours);
    $("donutLegend").replaceChildren();
    report.rows.forEach((r) => {
      const line = node("div", null, "legend-row"),
        dot = node("i");
      dot.style.background = colors[rooms.findIndex((room) => room.id === r.id)];
      line.append(
        dot,
        node("span", r.name),
        node("strong", num(report.hours ? (r.hours / report.hours) * 100 : 0) + "%"),
      );
      $("donutLegend").append(line);
    });
  }
  function renderTop() {
    $("topRooms").replaceChildren();
    const list = [...report.rows].sort((a, b) => b.count - a.count).slice(0, 5),
      max = Math.max(1, ...list.map((r) => r.count));
    list.forEach((r) => {
      const row = node("div", null, "top-row"),
        track = node("div", null, "track"),
        fill = node("i");
      fill.style.width = (r.count / max) * 100 + "%";
      track.append(fill);
      row.append(node("span", r.name), track, node("small", r.count + " lượt"));
      $("topRooms").append(row);
    });
    if (!list.length) $("topRooms").append(node("p", "Không có phòng phù hợp."));
  }
  function renderTable() {
    const query = $("searchRoom").value.trim().toLocaleLowerCase("vi"),
      rows = report.rows
        .filter((r) => r.name.toLocaleLowerCase("vi").includes(query))
        .sort((a, b) => (descending ? b.count - a.count : a.count - b.count));
    $("roomRows").replaceChildren();
    rows.forEach((r, index) => {
      const tr = node("tr");
      [index + 1, r.name, r.capacity, r.count, num(r.hours)].forEach((v) =>
        tr.append(node("td", v)),
      );
      const usage = node("td"),
        wrap = node("div", null, "usage"),
        track = node("span", null, "usage-track"),
        fill = node("i");
      fill.style.width = Math.min(100, r.utilization) + "%";
      fill.style.background =
        r.utilization >= 75 ? "#13b56c" : r.utilization >= 50 ? "#4993ff" : "#ffbc4c";
      track.append(fill);
      wrap.append(node("span", num(r.utilization) + "%"), track);
      usage.append(wrap);
      const status = node("td"),
        badge = node("span", r.status === "ACTIVE" ? "Đang hoạt động" : "Đang bảo trì", "badge");
      badge.dataset.status = r.status;
      status.append(badge);
      tr.append(usage, status);
      $("roomRows").append(tr);
    });
    if (!rows.length) {
      const tr = node("tr"),
        td = node("td", "Không tìm thấy phòng phù hợp.");
      td.colSpan = 7;
      td.className = "empty";
      tr.append(td);
      $("roomRows").append(tr);
    }
    $("tableCount").textContent =
      rows.length +
      " phòng · " +
      (report.bookings ? "Có dữ liệu trong kỳ báo cáo." : "Không có lượt đặt trong kỳ báo cáo.");
  }
  async function apply() {
    if (busy) return;
    const from = $("fromDate").value,
      to = $("toDate").value,
      a = timestamp(from),
      b = timestamp(to);
    $("filterError").hidden = true;
    if (!from || !to || !Number.isFinite(a) || !Number.isFinite(b) || b < a || b - a > DAY * 366) {
      $("filterError").textContent =
        "Chọn khoảng ngày hợp lệ, ngày kết thúc không trước ngày bắt đầu và không quá 367 ngày.";
      $("filterError").hidden = false;
      return;
    }
    busy = true;
    $("applyFilter").disabled = true;
    $("reportStatus").textContent = "Đang tổng hợp dữ liệu minh họa…";
    await new Promise((r) => setTimeout(r, 180));
    report = summarize(from, to, $("roomFilter").value, $("statusFilter").value);
    render();
    busy = false;
    $("applyFilter").disabled = false;
    $("reportStatus").textContent =
      "Dữ liệu minh họa · " +
      from.split("-").reverse().join("/") +
      " – " +
      to.split("-").reverse().join("/");
  }
  rooms.forEach((r) => {
    const o = node("option", r.name);
    o.value = r.id;
    $("roomFilter").append(o);
  });
  $("filters").addEventListener("submit", (e) => {
    e.preventDefault();
    document.querySelectorAll("[data-days]").forEach((b) => b.classList.remove("active"));
    apply();
  });
  document.querySelectorAll("[data-days]").forEach((button) =>
    button.addEventListener("click", () => {
      if (busy) return;
      const today = new Intl.DateTimeFormat("sv-SE", {
        timeZone: "Asia/Ho_Chi_Minh",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
      }).format(new Date());
      $("toDate").value = today;
      $("fromDate").value = key(timestamp(today) - (Number(button.dataset.days) - 1) * DAY);
      document
        .querySelectorAll("[data-days]")
        .forEach((b) => b.classList.toggle("active", b === button));
      apply();
    }),
  );
  $("searchRoom").addEventListener("input", () => {
    if (report) renderTable();
  });
  $("sortBookings").addEventListener("click", () => {
    descending = !descending;
    $("sortBookings").textContent = "Số lượt đặt " + (descending ? "↓" : "↑");
    if (report) renderTable();
  });
  $("menuToggle").addEventListener("click", () =>
    document.body.classList.toggle("sidebar-collapsed"),
  );
  $("exportCsv").addEventListener("click", () => {
    if (!report || busy) return;
    const escape = (v) => '"' + String(v).replace(/"/g, '""') + '"',
      rows = [
        ["Tên phòng", "Sức chứa", "Lượt đặt", "Giờ sử dụng", "Tỷ lệ (%)", "Trạng thái"],
        ...report.rows.map((r) => [
          r.name,
          r.capacity,
          r.count,
          r.hours,
          r.utilization,
          r.status === "ACTIVE" ? "Đang hoạt động" : "Đang bảo trì",
        ]),
      ],
      blob = new Blob(["\uFEFF" + rows.map((row) => row.map(escape).join(",")).join("\r\n")], {
        type: "text/csv;charset=utf-8",
      }),
      url = URL.createObjectURL(blob),
      link = node("a");
    link.href = url;
    link.download = "bao-cao-phong-" + report.from + "-" + report.to + ".csv";
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  });
  $("printReport").addEventListener("click", () => {
    if (!busy) window.print();
  });
  $("fromDate").value = "2026-10-01";
  $("toDate").value = "2026-10-31";
  apply();
})();
