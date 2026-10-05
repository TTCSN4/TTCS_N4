# API quản lý cuộc họp và trạng thái thiết bị

Ứng dụng chạy tại cổng `8080`. Mặc định dùng H2; profile `mysql` đọc cấu hình
từ `DB_URL`, `DB_USERNAME` và `DB_PASSWORD`.

## Tra cứu trạng thái thiết bị

`GET /api/equipment/status?room=P.101&at=2030-05-10T09:30:00%2B07:00`

`room` là mã phòng trên thiết bị (`roomId`); `at` là thời điểm ISO-8601 có
múi giờ. Mỗi mục trả về `equipmentId`, `name`, `room`, `totalQuantity`,
`bookedQuantity`, `maintenanceQuantity`, `availableQuantity` và `statuses`.
Trạng thái được trả về là `AVAILABLE`, `BOOKED` và/hoặc `MAINTENANCE` tùy theo
số lượng thuộc từng trạng thái tại thời điểm truy vấn.

## Đặt thiết bị

`POST /api/equipment/bookings`

```json
{
  "equipmentId": "EQ-123",
  "meetingId": 42,
  "room": "P.101",
  "quantity": 1,
  "startTime": "2030-05-10T09:00:00+07:00",
  "endTime": "2030-05-10T10:00:00+07:00"
}
```

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
