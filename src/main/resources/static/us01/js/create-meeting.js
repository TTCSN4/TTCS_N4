"use strict";
(() => {
  const $ = (id) => document.getElementById(id);
  const form = $("createMeetingForm");
  if (!form) return;
  const editingId = new URLSearchParams(location.search).get('meetingId');
  const state = {
    rooms: [],
    meetings: [],
    equipment: [],
    stock: [],
    participants: new Set(),
    plannedAttendeeCount: null,
    users: [],
    participantGroups: [],
    groupsLoading: true,
    groupsSaving: false,
    selectedEquipment: new Set(),
    equipmentQuantities: new Map(),
    ready: false,
    loading: false,
    stockReady: false,
    stockLoading: false,
    stockKey: "",
    stockError: "",
    busy: false,
    uncertain: false,
    stockSequence: 0,
  };
  const fieldIds = [
    "organizerId",
    "meetingTitle",
    "meetingDate",
    "startTime",
    "endTime",
    "meetingRoom",
    "participants",
    "equipment",
    "meetingNotes",
  ];
  function node(tag, text, className) {
    const element = document.createElement(tag);
    if (text !== undefined) element.textContent = text;
    if (className) element.className = className;
    return element;
  }
  const idValue = (value) =>
    typeof value === "string" && value.trim() && value.length <= 50;
  function localTime(date, time) {
    if (
      !/^\d{4}-\d{2}-\d{2}$/.test(date) ||
      !/^([01]\d|2[0-3]):[0-5]\d$/.test(time)
    )
      return NaN;
    const stamp = Date.parse(date + "T" + time + ":00+07:00");
    return Number.isFinite(stamp) &&
      new Date(stamp + 7 * 3600000).toISOString().slice(0, 16) ===
        date + "T" + time
      ? stamp
      : NaN;
  }
  function serverTime(value) {
    if (
      typeof value !== "string" ||
      !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?$/.test(value)
    )
      return NaN;
    return Date.parse(value + "+07:00");
  }
  function windowValue() {
    const date = $("meetingDate").value,
      startTime = $("startTime").value,
      endTime = $("endTime").value;
    const start = localTime(date, startTime),
      end = localTime(date, endTime);
    return {
      date,
      startTime,
      endTime,
      start,
      end,
      valid: Number.isFinite(start) && Number.isFinite(end) && start < end,
      key: [date, startTime, endTime, $("meetingRoom").value].join("|"),
    };
  }
  function attendeeIds() {
    return [
      ...new Set(
        [...state.participants, $("organizerId").value.trim()].filter(Boolean),
      ),
    ];
  }
  const overlaps = (first, second) =>
    first.start < second.end && second.start < first.end;
  function roomIssue(
    room,
    window = windowValue(),
    count = attendeeIds().length,
  ) {
    if (!room) return "Chọn phòng họp hợp lệ.";
    if (room.status === "CONSTRUCTION") return "Phòng đang thi công.";
    if (room.status === "MAINTENANCE") return "Phòng đang bảo trì.";
    if (["INACTIVE", "UNAVAILABLE", "DISABLED", "CLOSED"].includes(room.status))
      return "Phòng đã ngừng sử dụng.";
    if (!["AVAILABLE", "ACTIVE", "OCCUPIED"].includes(room.status))
      return "Phòng không khả dụng (" + room.status + ").";
    if (!window.valid) return "Chọn ngày và giờ hợp lệ để kiểm tra.";
    if (
      state.meetings.some(
        (meeting) =>
          meeting.id !== editingId && meeting.roomId === room.id &&
          meeting.status !== "CANCELLED" &&
          overlaps(meeting, window),
      )
    )
      return "Đã có lịch trong khoảng giờ chọn.";
    if (room.capacity < count)
      return "Không đủ sức chứa (" + count + "/" + room.capacity + " người).";
    return "";
  }
  function status(message, error = false) {
    $("formStatus").hidden = false;
    $("formStatus").dataset.error = String(error);
    $("formStatus").textContent = message;
  }
  function showErrors(errors, focus = false) {
    for (const id of fieldIds) {
      $(id + "Error").textContent = errors[id] || "";
      $(id)?.setAttribute("aria-invalid", String(Boolean(errors[id])));
    }
    if (focus) {
      const first = Object.keys(errors)[0];
      (first === "participants"
        ? $("participantId")
        : first === "equipment"
          ? $("equipment")
          : $(first)
      )?.focus();
    }
  }
  function csrfHeaders() {
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;
    if (token && header) return { [header]: token };
    const cookie = document.cookie
      .split(";")
      .map((part) => part.trim())
      .find((part) => part.startsWith("XSRF-TOKEN="));
    if (!cookie) return {};
    try {
      return {
        "X-XSRF-TOKEN": decodeURIComponent(cookie.slice("XSRF-TOKEN=".length)),
      };
    } catch {
      return {};
    }
  }
  async function request(url, { method = "GET", body } = {}) {
    const controller = new AbortController(),
      timer = setTimeout(() => controller.abort(), 15000);
    const write = method !== "GET";
    try {
      const response = await fetch(url, {
        method,
        credentials: "same-origin",
        cache: "no-store",
        signal: controller.signal,
        headers: {
          Accept: "application/json",
          ...(write
            ? { "Content-Type": "application/json", ...csrfHeaders() }
            : {}),
        },
        ...(body ? { body: JSON.stringify(body) } : {}),
      });
      let data;
      try {
        data = await response.json();
      } catch {
        const error = Error(
          "Máy chủ trả dữ liệu không hợp lệ. " +
            (write
              ? "Hãy kiểm tra danh sách lịch trước khi gửi lại."
              : "Kiểm tra API và phiên đăng nhập."),
        );
        error.status = response.status;
        error.uncertain =
          write &&
          response.status !== 400 &&
          response.status !== 401 &&
          response.status !== 403 &&
          response.status !== 404 &&
          response.status !== 409;
        throw error;
      }
      if (!response.ok) {
        const message =
          response.status === 401
            ? "Phiên đăng nhập chưa hợp lệ. Vui lòng đăng nhập và tải lại."
            : response.status === 403
              ? "Máy chủ từ chối quyền truy cập. Chủ trì phải có quyền Admin hoặc PM; kiểm tra phiên đăng nhập và cấu hình API."
              : data?.message ||
                data?.detail ||
                "API báo lỗi HTTP " + response.status + ".";
        const error = Error(message);
        error.status = response.status;
        error.fields = data?.errors;
        error.uncertain = write && response.status >= 500;
        throw error;
      }
      return { data, status: response.status };
    } catch (error) {
      if (!error.status) {
        error.message =
          error.name === "AbortError"
            ? "API phản hồi quá thời gian chờ."
            : "Không kết nối được API. Kiểm tra máy chủ cổng 8080 và kết nối mạng.";
        error.uncertain = write;
      }
      throw error;
    } finally {
      clearTimeout(timer);
    }
  }
  function validateList(list, type) {
    if (!Array.isArray(list))
      throw Error("API " + type + " không trả danh sách hợp lệ.");
    return list;
  }
  function uniqueRecords(records, label) {
    if (new Set(records.map((item) => item.id)).size !== records.length)
      throw Error("API " + label + " có mã bị trùng.");
    return records;
  }
  function mapRooms(list) {
    return uniqueRecords(
      validateList(list, "phòng").map((item) => {
        if (
          !item ||
          !idValue(item.roomId) ||
          typeof item.roomName !== "string" ||
          !item.roomName.trim() ||
          !Number.isInteger(item.capacity) ||
          item.capacity < 1 ||
          typeof item.status !== "string"
        )
          throw Error(
            "API phòng có thông tin mã, tên, trạng thái hoặc sức chứa không hợp lệ.",
          );
        return {
          id: item.roomId,
          name: item.roomName,
          capacity: item.capacity,
          status: item.status.toUpperCase(),
          location: item.location || "",
        };
      }),
      "phòng",
    );
  }
  function mapMeetings(list) {
    return uniqueRecords(
      validateList(list, "lịch họp").map((item) => {
        const start = serverTime(item?.startTime),
          end = serverTime(item?.endTime);
        if (
          !item ||
          !idValue(item.meetingId) ||
          typeof item.title !== "string" ||
          typeof item.status !== "string" ||
          (item.roomId !== null &&
            item.roomId !== undefined &&
            !idValue(item.roomId)) ||
          !Number.isFinite(start) ||
          !Number.isFinite(end) ||
          end <= start
        )
          throw Error(
            "API lịch họp có dữ liệu không hợp lệ; chưa thể kiểm tra phòng trống.",
          );
        return {
          id: item.meetingId,
          title: item.title,
          roomId: item.roomId || "",
          start,
          end,
          status: item.status.toUpperCase(),
          startTime: item.startTime,
          endTime: item.endTime,
        };
      }),
      "lịch họp",
    );
  }
  function mapEquipment(list) {
    return uniqueRecords(
      validateList(list, "thiết bị").map((item) => {
        if (
          !item ||
          !idValue(item.equipmentId) ||
          typeof item.equipmentName !== "string" ||
          typeof item.status !== "string" ||
          !Number.isInteger(item.totalQuantity) ||
          item.totalQuantity < 0 ||
          (item.roomId !== null &&
            item.roomId !== undefined &&
            !idValue(item.roomId))
        )
          throw Error("API thiết bị có dữ liệu không hợp lệ.");
        return {
          id: item.equipmentId,
          name: item.equipmentName,
          roomId: item.roomId || "",
          total: item.totalQuantity,
          status: item.status.toUpperCase(),
        };
      }),
      "thiết bị",
    );
  }
  function syncControls() {
    const locked = state.busy || state.loading;
    form.querySelectorAll("input,select,textarea,button").forEach((control) => {
      control.disabled = locked;
    });
    for(const id of ['participantGroup','participantPerson','inviteParticipantGroup','inviteParticipantPerson','saveParticipantGroup','participantGroupName']){if($(id))$(id).disabled=locked||state.groupsLoading||state.groupsSaving;}
    $("submitBtn").disabled = locked || !state.ready || state.uncertain;
    $("reloadData").disabled = locked;
    $("acknowledgeRequest").hidden = !state.uncertain;
    $("acknowledgeRequest").disabled = locked;
    $("clearParticipants").disabled = locked || state.participants.size === 0;
    $("equipmentOptions").querySelectorAll("button").forEach(button=>{button.disabled=locked;});
    $("equipmentOptions")
      .querySelectorAll("input")
      .forEach((control) => {
        const item = state.stock.find((stock) => stock.id === control.dataset.equipmentId);
        control.disabled =
          locked ||
          !state.stockReady ||
          !item ||
          item.available < 1 ||
          !item.usable || (control.type === "number" && !state.selectedEquipment.has(item.id));
      });
    $("participantOptions")
      .querySelectorAll("button")
      .forEach((control) => {
        control.disabled = locked;
      });
  }
  let roomRefreshTimer,roomRefreshSequence=0;
  function scheduleRoomRefresh(){
    clearTimeout(roomRefreshTimer);const sequence=++roomRefreshSequence;
    if(!windowValue().valid)return;
    roomRefreshTimer=setTimeout(async()=>{try{
      const [rooms,meetings]=await Promise.all([request('/api/rooms'),request('/api/meetings')]);
      if(sequence!==roomRefreshSequence)return;
      state.rooms=mapRooms(rooms.data);state.meetings=mapMeetings(meetings.data);
      renderRooms();renderSchedules();await refreshEquipment();
    }catch(error){status('Chưa kiểm tra lại được phòng trống: '+error.message,true);}},250);
  }
  function renderRooms() {
    const select=$("meetingRoom"), selected=select.value, time=windowValue();
    select.replaceChildren(node("option",state.ready?"Chọn phòng họp":"Chưa tải được phòng"));
    select.firstElementChild.value="";
    for(const room of state.rooms){
      if(!["AVAILABLE","ACTIVE","OCCUPIED"].includes(room.status)||room.capacity<attendeeIds().length)continue;
      if(time.valid&&roomIssue(room,time))continue;
      const option=node("option",room.name+" · "+room.capacity+" người"+(time.valid?" · Còn trống":" · Chọn thời gian để kiểm tra"));
      option.value=room.id;select.append(option);
    }
    if([...select.options].some(o=>o.value===selected))select.value=selected;
    else if(selected){state.selectedEquipment.clear();state.equipmentQuantities.clear();state.stockReady=false;renderEquipment();}
    if(select.options.length===1&&state.ready)select.firstElementChild.textContent="Không còn phòng phù hợp trong khoảng giờ này";
    summary();
  }
  function renderPeople() {
    $("participantOptions").replaceChildren();
    const organizer = $("organizerId").value.trim();
    for (const id of state.participants) {
      const row = node("div", undefined, "participant-token"),
        remove = node("button", "×");
      remove.type = "button";
      remove.setAttribute("aria-label", "Bỏ người tham dự " + id);
      remove.addEventListener("click", () => {
        if (state.busy || state.loading) return;
        state.participants.delete(id);
        renderPeople();
        renderRooms();
      });
      row.append(
        node("span", (state.users.find(u=>u.id===id)?.fullName||id) + (id === organizer ? " · Chủ trì" : "")),
        remove,
      );
      $("participantOptions").append(row);
    }
    if (!state.participants.size)
      $("participantOptions").append(
        node(
          "p",
          "Chưa thêm người khác. Mã chủ trì được tự động tính vào danh sách.",
          "field-hint",
        ),
      );
    summary();
    syncControls();
  }
  function renderEquipment() {
    const container=$('equipmentOptions');container.replaceChildren();
    let list=state.stock;
    if(!state.stockReady){const roomId=$('meetingRoom').value,preview=state.equipment.filter(e=>!roomId||e.roomId===roomId);list=roomId?preview:[...new Map(preview.map(e=>[e.name,e])).values()];container.append(node('p',state.stockLoading?'Đang kiểm tra số lượng thiết bị…':'Chọn thiết bị bên dưới. Cần có ngày, giờ và phòng để kiểm tra số lượng.','field-hint'));}
    for(const item of list){
      const selected=state.selectedEquipment.has(item.id),row=node('div',undefined,'equipment-choice'+(selected?' is-selected':'')),info=node('div',undefined,'equipment-identity'),action=node('button',selected?'Hủy':'Chọn','equipment-toggle'),quantityLabel=node('label',undefined,'equipment-quantity'),quantity=node('input');
      info.append(node('strong',item.name),node('small',!state.stockReady?'Chưa kiểm tra số lượng':!item.usable?'Thiết bị không hoạt động':'Còn '+item.available+'/'+item.total+' trong khoảng giờ đã chọn'));
      action.type='button';action.id='equipment-'+item.id;action.dataset.equipmentId=item.id;action.setAttribute('aria-pressed',String(selected));action.setAttribute('aria-label',(selected?'Hủy chọn ':'Chọn ')+item.name);
      action.addEventListener('click',()=>{
        if(state.selectedEquipment.has(item.id)){state.selectedEquipment.delete(item.id);state.equipmentQuantities.delete(item.id);}
        else {
          if(!state.stockReady){$('equipmentError').textContent=state.stockLoading?'Đang kiểm tra thiết bị, vui lòng thử lại sau giây lát.':'Vui lòng chọn ngày, giờ bắt đầu, giờ kết thúc và phòng trước khi chọn thiết bị.';const missing=['meetingDate','startTime','endTime','meetingRoom'].find(id=>!$(id).value);if(missing)$(missing).focus();return;}
          if(!item.usable||item.available<1){$('equipmentError').textContent='Thiết bị này không còn khả dụng trong khoảng giờ đã chọn. Hãy chọn thiết bị hoặc thời gian khác.';return;}
          state.selectedEquipment.add(item.id);state.equipmentQuantities.set(item.id,1);
        }
        $('equipmentError').textContent='';renderEquipment();summary();document.getElementById('equipment-'+item.id)?.focus();
      });
      quantity.type='number';quantity.min='1';quantity.max=String(item.available||item.total||1);quantity.step='1';quantity.id='quantity-'+item.id;quantity.dataset.equipmentId=item.id;quantity.value=String(state.equipmentQuantities.get(item.id)||1);quantity.setAttribute('aria-label','Số lượng '+item.name);quantity.addEventListener('input',()=>{state.equipmentQuantities.set(item.id,Number(quantity.value));$('equipmentError').textContent='';summary();});quantityLabel.htmlFor=quantity.id;quantityLabel.append(node('span','Số lượng'),quantity);row.append(info,quantityLabel,action);container.append(row);
    }
    if(!list.length)container.append(node('p',state.ready?'Chưa có thiết bị cho phòng này.':'Đang tải danh mục thiết bị…','field-hint'));syncControls();
  }
  function summary() {
if(globalThis.TTCSDemo)try{sessionStorage.setItem('meeting.us01.participants.v1',JSON.stringify({participantIds:[...state.participants],organizerId:$('organizerId').value.trim()||null,organizerAttends:!!$('organizerId').value.trim()}));}catch{}
    const count = attendeeIds().length,
      room = state.rooms.find((item) => item.id === $("meetingRoom").value),
      window = windowValue();
    $("participantCount").textContent = count + " người (gồm chủ trì)";
    $("participantCapacity").textContent =
      "Tổng số người: " +
      count +
      (room
        ? " · Sức chứa phòng: " + room.capacity + " người."
        : " · Chọn phòng để kiểm tra sức chứa.");
    if(state.plannedAttendeeCount)$('participantCapacity').textContent+=' · Số người dự kiến khi tìm phòng: '+state.plannedAttendeeCount+'; đã có '+count+' tài khoản trong lịch.';
    $("summaryDuration").textContent = window.valid
      ? (window.end - window.start) / 60000 + " phút"
      : "Chưa chọn";
    $("currentUserName").textContent =
      $("organizerId").value.trim() || "Người tổ chức";
    $("availabilityStatus").textContent = !state.ready
      ? "Chưa tải được dữ liệu để kiểm tra phòng trống."
      : !window.valid
        ? "Chọn ngày và giờ hợp lệ để kiểm tra."
        : "Đã đối chiếu lịch từ API: " +
          state.rooms.filter((item) => !roomIssue(item, window, count)).length +
          " phòng phù hợp. Lịch tiếp giáp không tính là trùng.";
    syncControls();
  }
  function renderSchedules() {
    $("meetingBadge").textContent = String(state.meetings.length);
    $("savedList").replaceChildren();
    if (!state.ready) {
      $("savedList").append(
        node("p", "Chưa tải được danh sách cuộc họp.", "saved-empty"),
      );
      return;
    }
    if (!state.meetings.length) {
      $("savedList").append(
        node("p", "Máy chủ chưa có cuộc họp nào.", "saved-empty"),
      );
      return;
    }
    for (const item of [...state.meetings].sort((a,b)=>b.start-a.start).slice(0, 20)) {
      const row = node("article", undefined, "saved-item"),
        info = node("div");
      info.append(
        node("strong", item.title),
        node(
          "p",
          item.id +
            " · " +
            (state.rooms.find((room) => room.id === item.roomId)?.name ||
              item.roomId ||
              "Không có phòng") +
            " · " +
            item.startTime.replace("T", " ") +
            " – " +
            item.endTime.replace("T", " ") +
            " · " +
            item.status,
        ),
      );
      row.append(info);
      if(item.status==="SCHEDULED"&&Date.parse(item.startTime)>Date.now()){const edit=node("button","Sửa đầy đủ","button secondary");edit.type="button";edit.onclick=()=>openEdit(item.id);row.append(edit);}
      $("savedList").append(row);
    }
    if (state.meetings.length > 20)
      $("savedList").append(
        node(
          "p",
          "Đang hiển thị 20 cuộc họp đầu trong " +
            state.meetings.length +
            " cuộc họp.",
          "field-hint",
        ),
      );
  }
  async function refreshEquipment() {
    const sequence = ++state.stockSequence,
      window = windowValue(),
      roomId = $("meetingRoom").value;
    state.stockReady = false;
    state.stockLoading = false;
    state.stockKey = "";
    state.stockError = "";
    if (!state.ready || !window.valid || !roomId) {
      state.stock = [];
      $("equipmentStatus").textContent =
        "Chọn phòng và thời gian hợp lệ để tải thiết bị.";
      renderEquipment();
      return;
    }
    state.stockLoading = true;
    $("equipmentStatus").textContent =
      "Đang tải số lượng thiết bị trong khoảng giờ đã chọn…";
    renderEquipment();
    try {
      const result = await request(
        "/api/equipment/status?" +
          new URLSearchParams({
            room: roomId,
            at: window.date + "T" + window.startTime + ":00+07:00",
            endTime: window.date + "T" + window.endTime + ":00+07:00",
            ...(editingId ? {excludeMeetingId:editingId} : {}),
          }),
      );
      if (sequence !== state.stockSequence || window.key !== windowValue().key)
        return;
      const raw = validateList(result.data, "trạng thái thiết bị"),
        base = state.equipment.filter(
          (item) => item.roomId.toLowerCase() === roomId.toLowerCase(),
        );
      const stock = raw.map((item) => {
        const equipment = base.find((entry) => entry.id === item?.equipmentId);
        if (
          !equipment ||
          !Number.isInteger(item.availableQuantity) ||
          item.availableQuantity < 0 ||
          !Number.isInteger(item.totalQuantity) ||
          item.totalQuantity !== equipment.total ||
          item.availableQuantity > item.totalQuantity ||
          item.room?.toLowerCase() !== roomId.toLowerCase()
        )
          throw Error("API trạng thái thiết bị không khớp danh mục phòng.");
        return {
          ...equipment,
          available: item.availableQuantity,
          usable: ["AVAILABLE", "ACTIVE", "IN_USE", "OCCUPIED"].includes(
            equipment.status,
          ),
        };
      });
      uniqueRecords(stock, "trạng thái thiết bị");
      if (stock.length !== base.length)
        throw Error("API trạng thái chưa trả đủ thiết bị của phòng.");
      state.stock = stock;
      state.stockReady = true;
      state.stockKey = window.key;
      $("equipmentStatus").textContent =
        "Khả dụng trong khoảng " +
        window.startTime +
        " ngày " +
        window.date +
        " · Đã kiểm tra khoảng " +
        window.startTime +
        "–" +
        window.endTime +
        ".";
    } catch (error) {
      if (sequence !== state.stockSequence) return;
      state.stock = [];
      state.stockError = error.message;
      $("equipmentStatus").textContent =
        "Không tải được thiết bị: " +
        error.message +
        " Có thể tạo cuộc họp không kèm thiết bị.";
    } finally {
      if (sequence === state.stockSequence) {
        state.stockLoading = false;
        renderEquipment();
        summary();
      }
    }
  }
  async function loadCatalog({ quiet = false } = {}) {
    if (state.loading) return false;
    state.loading = true;
    syncControls();
    if (!quiet) status("Đang tải phòng, lịch họp và thiết bị từ API…");
    try {
      if (location.protocol === "file:")
        throw Error(
          "Mở trang qua máy chủ http://localhost:8080, không mở trực tiếp file HTML.",
        );
      const results = await Promise.allSettled([
        request("/api/rooms"),
        request("/api/meetings"),
        request("/api/equipment"),
      ]);
      if (results[0].status === "rejected") throw results[0].reason;
      if (results[1].status === "rejected") throw results[1].reason;
      const rooms = mapRooms(results[0].value.data),
        meetings = mapMeetings(results[1].value.data);
      state.rooms = rooms;
      state.meetings = meetings;
      state.ready = true;
      if (results[2].status === "fulfilled")
        state.equipment = mapEquipment(results[2].value.data);
      else {
        state.equipment = [];
        state.stockError = results[2].reason.message;
      }
      renderRooms();
      renderSchedules();
      if (!quiet)
        status(
          "Đã tải dữ liệu API. Điền mã chủ trì, thời gian, phòng và người tham dự; máy chủ kiểm tra quyền khi lưu.",
        );
      return true;
    } catch (error) {
      state.ready = false;
      state.rooms = [];
      state.meetings = [];
      state.equipment = [];
      state.stockReady = false;
      state.stock = [];
      renderRooms();
      renderSchedules();
      renderEquipment();
      status("Không tải được dữ liệu: " + error.message, true);
      return false;
    } finally {
      state.loading = false;
      syncControls();
    }
  }
  function buildRequest() {
    const window = windowValue();
    return {
      organizerId: $("organizerId").value.trim(),
      roomId: $("meetingRoom").value,
      title: $("meetingTitle").value.trim(),
      description: $("meetingNotes").value.trim(),
      startTime: window.date + "T" + window.startTime + ":00",
      endTime: window.date + "T" + window.endTime + ":00",
      participants: attendeeIds(),
      equipmentIds: [...state.selectedEquipment],
      equipment: [...state.selectedEquipment].map(equipmentId=>({equipmentId,quantity:state.equipmentQuantities.get(equipmentId)||1})),
      isRecurring: !!state.editingMeta?.isRecurring,
      recurrenceRule: state.editingMeta?.recurrenceRule||null,
    };
  }
  function validate() {
    const errors = {},
      organizer = $("organizerId").value.trim(),
      window = windowValue(),
      title = $("meetingTitle").value.trim();
    if (!idValue(organizer))
      errors.organizerId =
        "Nhập mã chủ trì từ 1 đến 50 ký tự đang có trong hệ thống.";
    if (!title || title.length > 120)
      errors.meetingTitle = "Nhập tên cuộc họp từ 1 đến 120 ký tự.";
    if (!Number.isFinite(window.start) || window.start <= Date.now())
      errors.meetingDate =
        "Chọn ngày và giờ bắt đầu hợp lệ trong tương lai (giờ Việt Nam).";
    if (!window.startTime) errors.startTime = "Chọn giờ bắt đầu.";
    if (!window.valid)
      errors.endTime = "Giờ kết thúc phải sau giờ bắt đầu trong cùng ngày.";
    const issue = roomIssue(
      state.rooms.find((room) => room.id === $("meetingRoom").value),
    );
    if (issue) errors.meetingRoom = issue;
    if (attendeeIds().some((id) => !idValue(id)))
      errors.participants = "Mã người tham dự phải có từ 1 đến 50 ký tự.";
    if ($("participantId").value.trim())
      errors.participants =
        "Nhấn Thêm để đưa mã đang nhập vào danh sách, hoặc xóa ô mã chưa thêm.";
    if (
      state.selectedEquipment.size &&
      (!state.stockReady ||
        state.stockKey !== window.key ||
        [...state.selectedEquipment].some(
          (id) =>
            !state.stock.some(
              (item) => item.id === id && item.usable && Number.isInteger(state.equipmentQuantities.get(id)) && state.equipmentQuantities.get(id)>0 && state.equipmentQuantities.get(id)<=item.available,
            ),
        ))
    )
      errors.equipment =
        "Chọn số lượng nguyên dương không vượt số lượng khả dụng trong khoảng giờ, hoặc bỏ chọn thiết bị.";
    if ($("meetingNotes").value.length > 2000)
      errors.meetingNotes = "Ghi chú tối đa 2.000 ký tự.";
    return errors;
  }
  function sameIds(first, second) {
    return (
      Array.isArray(first) &&
      first.every((id) => typeof id === "string") &&
      new Set(first).size === first.length &&
      first.length === second.length &&
      first.every((id) => second.includes(id))
    );
  }
  function validateCreated(result, body) {
    const item = result.data;
    if (
      result.status !== (editingId ? 200 : 201) ||
      !item ||
      !idValue(item.meetingId) ||
      item.title !== body.title ||
      item.organizerId !== body.organizerId ||
      item.roomId !== body.roomId ||
      item.description !== body.description ||
      serverTime(item.startTime) !== serverTime(body.startTime) ||
      serverTime(item.endTime) !== serverTime(body.endTime) ||
      item.status !== "SCHEDULED" ||
      item.isRecurring !== body.isRecurring ||
      !sameIds(item.participants, body.participants) ||
      !sameIds(item.equipmentIds, body.equipmentIds) || !Array.isArray(item.equipment) || body.equipment.some(e=>!item.equipment.some(saved=>saved.equipmentId===e.equipmentId&&saved.quantity===e.quantity))
    ) {
      const error = Error(
        "Kết quả lưu không khớp yêu cầu. Hãy tải và kiểm tra danh sách cuộc họp trước khi gửi lại để tránh tạo trùng.",
      );
      error.uncertain = true;
      throw error;
    }
    return item;
  }
  function renderParticipantGroups(){
    const select=$('participantGroup'),selected=select.value;select.replaceChildren(new Option('Chọn nhóm người tham gia',''));
    for(const group of state.participantGroups)select.append(new Option(group.name+' ('+group.memberIds.length+' người)',group.id));
    if(state.participantGroups.some(g=>g.id===selected))select.value=selected;
    const person=$('participantPerson'),old=person.value;person.replaceChildren(new Option('Chọn người tham gia',''));for(const user of state.users)person.append(new Option(user.fullName+' · '+(user.department||user.id),user.id));person.value=old;
    previewParticipantGroup();syncControls();
  }
  function previewParticipantGroup(){const group=state.participantGroups.find(g=>g.id===$('participantGroup').value);$('participantGroupPreview').textContent=group?(group.memberIds.length?group.memberIds.map(id=>state.users.find(u=>u.id===id)?.fullName||id).join(', '):'Nhóm chưa có thành viên đang hoạt động.'):'Chọn nhóm để xem thành viên. Mời cả nhóm sẽ thêm từng người vào lịch, không thêm trùng.';}
  async function loadParticipantGroups(){state.groupsLoading=true;syncControls();try{const [groups,users]=await Promise.all([request('/api/participant-groups'),request('/api/users')]);state.participantGroups=validateList(groups.data,'nhóm người tham gia');state.users=validateList(users.data,'người dùng').filter(u=>u.status==='ACTIVE');$('participantGroupStatus').textContent='Có thể mời theo phòng ban hoặc lưu nhóm riêng cho sự kiện.';renderParticipantGroups();renderPeople();}catch(error){$('participantGroupStatus').textContent='Chưa tải được nhóm: '+error.message;}finally{state.groupsLoading=false;syncControls();}}
  $('participantGroup').addEventListener('change',previewParticipantGroup);
  $('inviteParticipantGroup').addEventListener('click',()=>{if(state.busy||state.loading||state.groupsLoading)return;const group=state.participantGroups.find(g=>g.id===$('participantGroup').value);if(!group?.memberIds.length){$('participantGroupStatus').textContent='Chọn nhóm có thành viên đang hoạt động.';return;}const before=attendeeIds().length;group.memberIds.forEach(id=>state.participants.add(id));$('participantsError').textContent='';renderPeople();renderRooms();$('participantGroupStatus').textContent='Đã thêm '+(attendeeIds().length-before)+' người từ '+group.name+'. Người đã có trong lịch không bị thêm trùng. Có thể bỏ riêng từng người bên dưới.';});
  $('inviteParticipantPerson').addEventListener('click',()=>{const id=$('participantPerson').value;if(!id||state.busy||state.loading)return;state.participants.add(id);renderPeople();renderRooms();$('participantPerson').value='';$('participantsError').textContent='';});
  $('saveParticipantGroup').addEventListener('click',async()=>{if(state.groupsSaving||state.groupsLoading||state.busy||state.loading)return;const name=$('participantGroupName').value.trim(),memberIds=[...state.participants].filter(id=>state.users.some(u=>u.id===id));if(!name||!memberIds.length){$('participantGroupStatus').textContent='Nhập tên nhóm và thêm ít nhất một người vào danh sách trước khi lưu nhóm.';return;}state.groupsSaving=true;syncControls();try{const {data:group}=await request('/api/participant-groups',{method:'POST',body:{name,memberIds}});state.participantGroups.push(group);renderParticipantGroups();$('participantGroup').value=group.id;previewParticipantGroup();$('participantGroupName').value='';$('participantGroupStatus').textContent='Đã lưu nhóm '+group.name+'. Có thể mời lại nhóm này khi tạo hoặc chỉnh sửa các lịch khác.';}catch(error){$('participantGroupStatus').textContent=error.message;}finally{state.groupsSaving=false;syncControls();}});
  function addParticipants() {
    if (state.busy || state.loading) return;
    const raw = $("participantId").value.trim(),
      ids = raw
        .split(/[,;\n]+/)
        .map((id) => id.trim())
        .filter(Boolean);
    if (!ids.length || ids.some((id) => !idValue(id))) {
      $("participantsError").textContent =
        "Nhập mã người tham dự hợp lệ, mỗi mã tối đa 50 ký tự.";
      $("participantId").focus();
      return;
    }
    ids.forEach((id) => state.participants.add(id));
    $("participantId").value = "";
    $("participantsError").textContent = "";
    renderPeople();
    renderRooms();
  }
  $("clearEquipment").addEventListener("click", () => {
    if (state.busy || state.loading) return;
    state.selectedEquipment.clear();
    state.equipmentQuantities.clear();
    $("equipmentError").textContent = "";
    renderEquipment();
    summary();
  });
  $("addParticipant").addEventListener("click", addParticipants);
  $("participantId").addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      addParticipants();
    }
  });
  $("clearParticipants").addEventListener("click", () => {
    if (state.busy || state.loading) return;
    state.participants.clear();
    renderPeople();
    renderRooms();
  });
  $("organizerId").addEventListener("input", () => {
    $("organizerIdError").textContent = "";
    renderPeople();
    renderRooms();
  });
  ["meetingDate", "startTime", "endTime"].forEach((id) =>
    $(id).addEventListener("input", () => {
      renderRooms();
      refreshEquipment();
      scheduleRoomRefresh();
    }),
  );
  $("meetingRoom").addEventListener("change", () => {
    state.selectedEquipment.clear();
    state.equipmentQuantities.clear();
    showErrors({});
    summary();
    refreshEquipment();
  });
  form.addEventListener("input", () => {
    if (!state.uncertain && !state.busy) $("formStatus").hidden = true;
    summary();
  });
  $("resetBtn").addEventListener("click", () => {
    if(editingId){loadEditing();return;}
    if (state.busy || state.loading || !confirm("Xóa thông tin đang nhập?"))
      return;
    form.reset();state.plannedAttendeeCount=null;
    state.participants.clear();
    state.selectedEquipment.clear();
    state.equipmentQuantities.clear();
    showErrors({});
    renderPeople();
    renderRooms();
    refreshEquipment();
    if (!state.uncertain) $("formStatus").hidden = true;
  });
  $("reloadData").addEventListener("click", async () => {
    if (state.busy || state.loading) return;
    await loadCatalog();
    await refreshEquipment();
    if (state.uncertain)
      status(
        "Đã tải lại dữ liệu. Yêu cầu trước có thể đã được lưu; kiểm tra danh sách và chỉ xác nhận khi chắc chắn cần gửi thêm.",
        true,
      );
  });
  $("acknowledgeRequest").addEventListener("click", () => {
    if (
      state.busy ||
      state.loading ||
      !confirm(
        "Bạn đã kiểm tra lịch trên máy chủ và xác nhận thao tác tiếp theo sẽ không tạo cuộc họp trùng?",
      )
    )
      return;
    state.uncertain = false;
    status(
      "Đã mở lại biểu mẫu sau khi bạn xác nhận kiểm tra lịch. Nội dung đang nhập được giữ nguyên.",
    );
    syncControls();
  });
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (state.busy || state.loading || !state.ready || state.uncertain) return;
    let errors = validate();
    showErrors(errors, true);
    if (Object.keys(errors).length) {
      status("Kiểm tra các thông tin được đánh dấu.", true);
      return;
    }
    state.busy = true;
    syncControls();
    status("Đang kiểm tra lại lịch phòng trước khi gửi…");
    let created = null;
    try {
      if (!(await loadCatalog({ quiet: true }))) return;
      await refreshEquipment();
      errors = validate();
      showErrors(errors, true);
      if (Object.keys(errors).length) {
        status(
          "Dữ liệu đã thay đổi. Kiểm tra các thông tin được đánh dấu.",
          true,
        );
        return;
      }
      const body = buildRequest();
      status(editingId ? "Đang lưu thay đổi lịch và thiết bị…" : "Đang tạo lịch và đặt thiết bị…");
      created = validateCreated(
        await request(editingId ? "/api/meetings/"+encodeURIComponent(editingId) : "/api/meetings/create", { method: editingId ? "PUT" : "POST", body }),
        body,
      );
      if(!editingId)form.reset();state.plannedAttendeeCount=null;
      state.participants.clear();
      state.selectedEquipment.clear();
    state.equipmentQuantities.clear();
      showErrors({});
      if(editingId)applyMeeting(created);
      renderPeople();
      if (!(await loadCatalog({ quiet: true })))
        status(
          "Đã tạo cuộc họp " +
            created.meetingId +
            " trên máy chủ, nhưng chưa tải lại được danh sách. Không gửi lại yêu cầu này.",
          true,
        );
      else
        status(
          (editingId ? "Đã cập nhật cuộc họp “" : "Đã tạo cuộc họp “") +
            created.title +
            "” trên máy chủ. Mã: " +
            created.meetingId +
            ". Tổng số người: " +
            body.participants.length +
            ".",
        );
      await refreshEquipment();
      $("formStatus").scrollIntoView({ behavior: "smooth", block: "center" });
    } catch (error) {
      if (error.uncertain) state.uncertain = true;
      const mapped = {},
        fields = {
          organizerId: "organizerId",
          title: "meetingTitle",
          roomId: "meetingRoom",
          startTime: "startTime",
          endTime: "endTime",
          participants: "participants",
          equipmentIds: "equipment",
          description: "meetingNotes",
        };
      if (error.fields && typeof error.fields === "object")
        for (const [field, message] of Object.entries(error.fields))
          if (fields[field] && typeof message === "string")
            mapped[fields[field]] = message;
      showErrors(mapped, true);
      status(
        error.message +
          (state.uncertain
            ? " Yêu cầu có thể đã được lưu; nút Tạo lịch đã khóa để tránh trùng. Tải lại và kiểm tra danh sách trước khi mở lại."
            : " Nội dung đang nhập được giữ nguyên."),
        true,
      );
    } finally {
      state.busy = false;
      syncControls();
    }
  });
  function openEdit(id){if(parent!==window)parent.postMessage({type:'ttcs-edit-meeting',id},location.origin);else location.href='/us01/templates/create-meeting.html?meetingId='+encodeURIComponent(id);}
  function applyMeeting(m){state.editingMeta=m;$('organizerId').value=m.organizerId;$('meetingTitle').value=m.title;$('meetingDate').value=m.startTime.slice(0,10);$('startTime').value=m.startTime.slice(11,16);$('endTime').value=m.endTime.slice(11,16);$('meetingNotes').value=m.description||'';state.participants=new Set(m.participants||[]);state.selectedEquipment=new Set((m.equipment||[]).map(e=>e.equipmentId));state.equipmentQuantities=new Map((m.equipment||[]).map(e=>[e.equipmentId,e.quantity]));renderPeople();renderRooms();$('meetingRoom').value=m.roomId;summary();}
  async function applyRoomSelection(){
    const params=new URLSearchParams(location.search);if(params.get('from')!=='us07'||!params.get('selectionId')){await refreshEquipment();return;}
    try{
      const raw=sessionStorage.getItem('meeting.us07.selection');const draft=raw?JSON.parse(raw):null;
      if(!draft||draft.selectionId!==params.get('selectionId')||draft.selectedBy!==window.TTCSDemo?.state.actor.id||!Number.isFinite(draft.selectedAt)||Date.now()-draft.selectedAt>15*60000||draft.selectedAt>Date.now()+60000)throw Error('Thông tin chọn phòng đã hết hạn hoặc không hợp lệ. Vui lòng tìm và chọn lại phòng.');
      if(!idValue(draft.roomId)||!/^\d{4}-\d{2}-\d{2}$/.test(draft.date)||!/^\d{2}:\d{2}$/.test(draft.startTime)||!/^\d{2}:\d{2}$/.test(draft.endTime)||!Number.isInteger(draft.participantCount)||draft.participantCount<1||draft.participantCount>200)throw Error('Thông tin phòng hoặc thời gian không hợp lệ.');
      state.plannedAttendeeCount=draft.participantCount;
      $('meetingDate').value=draft.date;$('startTime').value=draft.startTime;$('endTime').value=draft.endTime;
      $('organizerId').value=idValue(draft.organizerId)?draft.organizerId:window.TTCSDemo.state.actor.id;
      state.participants=new Set((Array.isArray(draft.participantIds)?draft.participantIds:[]).filter(idValue));state.selectedEquipment.clear();state.equipmentQuantities.clear();
      renderPeople();renderRooms();const room=state.rooms.find(r=>r.id===draft.roomId),time=windowValue();
      if(!time.valid||time.start<=Date.now()||!room||roomIssue(room,time)||room.capacity<draft.participantCount){$('meetingRoom').value='';await refreshEquipment();throw Error('Phòng vừa chọn đã thay đổi, không đủ chỗ hoặc không còn trống trong khoảng giờ này. Ngày giờ đã được giữ; vui lòng chọn phòng khác.');}
      $('meetingRoom').value=room.id;summary();await refreshEquipment();
      const actual=attendeeIds().length;status('Đã điền '+room.name+', ngày '+draft.date+', '+draft.startTime+'–'+draft.endTime+'. '+(actual<draft.participantCount?'Bạn tìm phòng cho '+draft.participantCount+' người; danh sách hiện có '+actual+' người. Hãy thêm thành viên hoặc mời nhóm. ':'Đã giữ danh sách người tham gia. ')+'Nhập tên cuộc họp, chọn thiết bị nếu cần và nhấn Tạo lịch để xác nhận.');
    }catch(error){status(error.message,true);}
  }
  async function loadEditing(){try{const {data:m}=await request('/api/meetings/'+encodeURIComponent(editingId));if(m.status!=='SCHEDULED')throw Error('Chỉ sửa được cuộc họp chưa bắt đầu và chưa hủy.');applyMeeting(m);document.querySelector('h1').textContent='Chỉnh sửa lịch họp';$('submitBtn').textContent='Lưu thay đổi';$('resetBtn').textContent='Khôi phục dữ liệu đã lưu';await refreshEquipment();status('Đã tải đầy đủ thông tin. Có thể sửa phòng, thời gian, người tham dự, thiết bị và số lượng; bỏ chọn để trả thiết bị.');}catch(e){state.ready=false;syncControls();status(e.message,true);}}
  window.US01 = {
    reload: async () => {
      if (!state.busy) {
        await loadCatalog();
        await refreshEquipment();
      }
    },
    localTime,
    serverTime,
    overlaps,
    attendeeIds,
    buildRequest,
    validate,
  };
  renderPeople();
  renderRooms();
  renderEquipment();
  renderSchedules();
  let liveRefreshTimer;
  function refreshLiveData(){clearTimeout(liveRefreshTimer);if(state.busy||state.loading||state.groupsSaving){liveRefreshTimer=setTimeout(refreshLiveData,700);return;}loadCatalog({quiet:true}).then(async ok=>{if(ok){await refreshEquipment();renderPeople();status('Đã cập nhật phòng, lịch và thiết bị mới nhất. Thông tin bạn đang nhập được giữ nguyên.');}});loadParticipantGroups();}
  window.addEventListener('ttcs-data-updated',event=>{event.preventDefault();refreshLiveData();});
  loadParticipantGroups();
  loadCatalog().then(ok=>{if(!ok)return;return editingId?loadEditing():applyRoomSelection();});
})();
