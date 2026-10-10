# US16 - Thông báo nhắc lịch họp qua email

Ứng dụng Spring Boot tự động gửi email lời mời, thông báo thay đổi/hủy và email nhắc trước giờ họp 15 phút. Email chỉ được gửi khi trạng thái cuộc họp phù hợp, người nhận có email hợp lệ, tài khoản còn hoạt động, người tham dự chưa từ chối/bị xóa và sự kiện đó chưa được gửi.

## Chạy ứng dụng

Yêu cầu Java 21, Maven và MySQL. Thiết lập biến môi trường trước khi chạy:

```powershell
$env:DB_USERNAME='root'
$env:DB_PASSWORD='mat-khau'
$env:SMTP_HOST='smtp.example.com'
$env:SMTP_PORT='587'
$env:SMTP_USERNAME='username'
$env:SMTP_PASSWORD='password'
$env:SMTP_AUTH='true'
$env:SMTP_STARTTLS='true'
$env:MAIL_FROM='meeting@example.com'
mvn spring-boot:run
```

Ứng dụng chạy tại `http://localhost:8080`. Có thể thay đổi chu kỳ quét bằng `REMINDER_SCAN_MS` và số lần gửi lại tối đa bằng `REMINDER_MAX_RETRIES` (mặc định 3).

## Tạo cuộc họp và lịch nhắc tự động

`POST /api/meetings`

```json
{
  "title": "Họp nhóm dự án",
  "description": "Tổng kết sprint",
  "startTime": "2026-10-12T14:00:00+07:00",
  "endTime": "2026-10-12T15:00:00+07:00",
  "organizerId": "user-001",
  "organizerName": "Nguyễn Văn A",
  "organizerEmail": "a@example.com",
  "location": "Phòng A101",
  "detailUrl": "http://localhost:8080/meetings/meeting-id",
  "participants": [
    {"userId": "user-002", "fullName": "Trần Văn B", "email": "b@example.com", "accountActive": true}
  ]
}
```

Khi tạo, hệ thống xếp email lời mời cho người tham dự và sinh notification nhắc lúc `startTime - 15 phút` cho người tổ chức cùng từng người tham dự hợp lệ. Ràng buộc duy nhất `(meeting_id, user_id, type, scheduled_at)` ngăn gửi trùng.

## API liên quan

- `PUT /api/meetings/{id}`: đổi thời gian/địa điểm; lịch nhắc cũ bị hủy và lịch 15 phút mới được tạo.
- `POST /api/meetings/{id}/cancel`: hủy cuộc họp và toàn bộ reminder chưa gửi.
- `POST /api/meetings/{id}/participants`: thêm/mời người tham dự.
- `PATCH /api/meetings/{id}/participants/{userId}/DECLINED`: từ chối; có thể dùng `REMOVED`, `INVITED`, `ACCEPTED`.
- `GET /api/meetings/{id}/reminders`: xem lịch sử gửi, trạng thái, số lần retry và lỗi gần nhất.
- `POST /api/meetings/{id}/reminders`: API tương thích để tạo reminder tùy chọn `EMAIL` hoặc `APP`.
- `POST /api/reminders/dispatch`: chạy quét ngay để thử nghiệm mà không cần chờ chu kỳ một phút.

Loại notification gồm `MEETING_INVITATION`, `MEETING_REMINDER_15_MINUTES`, `MEETING_UPDATED`, `MEETING_CANCELLED`. Trạng thái gồm `PENDING`, `PROCESSING`, `SENT`, `FAILED`, `CANCELLED`. Lỗi SMTP được lưu trong `error_message`; scheduler thử lại sau 5, 10, rồi tối đa 60 phút tùy số lần lỗi.

## Kiểm thử

```powershell
mvn test
```

Bộ test kiểm tra: chưa nhắc khi còn 30 phút, gửi đúng mốc 15 phút, không gửi trùng, loại trừ cuộc họp đã hủy/người từ chối/tài khoản vô hiệu hóa, retry khi SMTP lỗi, cập nhật reminder khi đổi giờ và kích hoạt lại reminder khi mời lại người dùng.
