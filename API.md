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

## Đặt thiết bị

`POST /api/equipment/bookings`

```json
{
  "equipmentId": 1,
  "meetingId": 1,
  "room": "P.101",
  "quantity": 1,
  "startTime": "2030-05-10T09:00:00+07:00",
  "endTime": "2030-05-10T10:00:00+07:00"
}
```

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
