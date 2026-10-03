# TTCS_N4_2 - API tra cứu trạng thái thiết bị

Ứng dụng tra cứu trạng thái thiết bị tại một thời điểm theo phòng. Máy chủ chạy
ở cổng `8080`.

## Tra cứu

`GET /api/equipment/status?room=P.101&at=2030-05-10T09:30:00%2B07:00`

Trả về danh sách thiết bị của phòng cùng tổng số lượng, số lượng đã đặt, số
lượng bảo trì, số lượng còn khả dụng và danh sách trạng thái hiện có:
`AVAILABLE`, `BOOKED`, `MAINTENANCE`. Một thiết bị có thể có nhiều trạng thái
đồng thời nếu các số lượng khác nhau đang ở từng trạng thái.

Danh sách và tạo thiết bị dùng `GET /api/equipment` và `POST /api/equipment`.
Để ghi nhận khoảng thời gian bảo trì, gửi:

`POST /api/equipment/maintenance`

```json
{
  "equipmentId": 1,
  "room": "P.101",
  "quantity": 1,
  "startTime": "2030-05-10T09:00:00+07:00",
  "endTime": "2030-05-10T10:00:00+07:00"
}
```

Mặc định ứng dụng dùng H2 cục bộ. Để trạng thái `BOOKED` phản ánh lượt đặt từ
`TTCS_N4_1`, hãy chạy cả hai ứng dụng với profile `mysql` và cùng cấu hình
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
