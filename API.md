<<<<<<< HEAD
# API quản lý cuộc họp và trạng thái thiết bị

Ứng dụng chạy tại cổng `8080`. Mặc định dùng H2; profile `mysql` đọc cấu hình
từ `DB_URL`, `DB_USERNAME` và `DB_PASSWORD`.

Giao diện tạo cuộc họp và đặt thiết bị từ frontend US12 có tại `/create-meeting.html`; trang
này nạp phòng từ danh sách thiết bị trên backend và đặt thiết bị cùng cuộc họp.

## Tra cứu trạng thái thiết bị

`GET /api/equipment/status?room=P.101&at=2030-05-10T09:30:00%2B07:00`

`room` là mã phòng trên thiết bị (`roomId`); `at` là thời điểm ISO-8601 có
múi giờ. Mỗi mục trả về `equipmentId`, `name`, `room`, `totalQuantity`,
`bookedQuantity`, `maintenanceQuantity`, `availableQuantity` và `statuses`.
Trạng thái được trả về là `AVAILABLE`, `BOOKED` và/hoặc `MAINTENANCE` tùy theo
số lượng thuộc từng trạng thái tại thời điểm truy vấn.
=======
# TTCS_N4_1 - API đặt thiết bị

Ứng dụng cung cấp API đặt thiết bị cho phòng họp. Máy chủ chạy ở cổng `8080`.

## Tạo thiết bị

`POST /api/equipment`

```json
{
  "name": "Máy chiếu",
  "room": "P.101",
  "quantity": 2
}
```
>>>>>>> e1f3f6bac2a4ff949c9d212475e9ffdfb21b5c94

## Đặt thiết bị

`POST /api/equipment/bookings`

```json
{
<<<<<<< HEAD
  "equipmentId": "EQ-123",
  "meetingId": 42,
=======
  "equipmentId": 1,
  "meetingId": 1,
>>>>>>> e1f3f6bac2a4ff949c9d212475e9ffdfb21b5c94
  "room": "P.101",
  "quantity": 1,
  "startTime": "2030-05-10T09:00:00+07:00",
  "endTime": "2030-05-10T10:00:00+07:00"
}
```

<<<<<<< HEAD
`meetingId` có thể bỏ qua nếu lượt đặt không gắn với cuộc họp. Khi có
`meetingId`, cuộc họp phải tồn tại, đang được lên lịch, cùng phòng và bao trùm
khung giờ đặt. Hệ thống kiểm tra số lượng trên toàn khoảng thời gian và trả
HTTP `409` nếu vượt quá tồn kho. Các khoảng thời gian được hiểu theo dạng
nửa mở `[startTime, endTime)`, do đó hai lượt đặt nối tiếp nhau không bị coi là
trùng.

Hủy lượt đặt bằng `DELETE /api/equipment/bookings/{bookingId}`. Hủy cuộc họp
cũng hủy các lượt đặt thiết bị gắn với cuộc họp đó.

## Bảo trì

`POST /api/equipment/maintenance` nhận cùng payload thời gian và số lượng như
API đặt thiết bị, nhưng không cần `meetingId`. Số lượng bảo trì cũng được kiểm
tra với các lượt đặt và bảo trì đang tồn tại.

## Quản lý thiết bị

`GET /api/equipment` liệt kê thiết bị. Tạo thiết bị bằng
`POST /api/equipment` với payload phù hợp với US14:

```json
{
  "roomId": "P.101",
  "equipmentName": "Máy chiếu",
  "type": "PROJECTOR",
  "totalQuantity": 3,
  "status": "AVAILABLE"
}
```

Trong giao diện, thiết bị có thể được chọn ngay khi tạo cuộc họp. Với cuộc họp
lặp lại, lượt đặt được tạo cho từng lần diễn ra; nếu có lỗi, giao diện hủy các
lượt đặt và cuộc họp vừa tạo.
=======
`meetingId` có thể bỏ trống hoặc để `null`. Khi có giá trị, cuộc họp phải tồn tại,
đang được lên lịch, bao trùm khung giờ đặt và không khác phòng được yêu cầu.
Thiết bị phải thuộc phòng đó. Đặt vượt số lượng khả dụng trong bất kỳ phần nào
của khung giờ bị từ chối với HTTP `409 Conflict`; khoảng giờ được xem là
`[startTime, endTime)`, vì vậy hai lượt đặt liền kề không bị xem là trùng.

## Hủy đặt

`DELETE /api/equipment/bookings/{id}`

Danh sách thiết bị có thể lấy từ `GET /api/equipment`; cuộc họp có thể tạo và
tra cứu qua `/api/meetings`.

Mặc định ứng dụng dùng H2 cục bộ. Để API tra cứu trong `TTCS_N4_2` nhìn thấy các
lượt đặt này, hãy chạy cả hai ứng dụng với profile `mysql` và cùng cấu hình
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
>>>>>>> e1f3f6bac2a4ff949c9d212475e9ffdfb21b5c94
