# US25 Đặt phòng qua chatbot

Trang chính là giao diện trò chuyện với chatbot, không phải form đặt phòng. Mỗi tin nhắn được gửi tới `POST /api/chatbot/message`; backend giữ trạng thái phiên và lần lượt hỏi mã người tổ chức, tiêu đề, thời gian, số người, danh sách người tham dự, phòng mong muốn và xác nhận cuối cùng. Người dùng có thể nhập `làm lại` ở bất kỳ bước nào.

Sau khi nhận đủ thời gian và số người, backend truy vấn CSDL để chỉ trả về các phòng đang hoạt động, đủ sức chứa và không trùng các cuộc họp `SCHEDULED`/`CONFIRMED`. Người dùng chọn phòng ngay trong cửa sổ chat hoặc chọn `tự chọn`. Khi người dùng nhập `xác nhận`, backend kiểm tra phòng trống lần cuối rồi mới tạo `meetings` cùng `meeting_participants`; vì vậy Gemini không được tự ý đặt phòng hoặc ghi CSDL. US16 có thể đọc các bản ghi này từ database chung và tạo email nhắc. API cấu trúc `POST /api/chatbot/book-room` vẫn được giữ để kiểm thử trực tiếp; `GET /api/rooms/available` dùng để kiểm tra danh sách phòng trống theo `startTime`, `endTime` và `attendees`.

Ví dụ body:

```json
{
  "organizerId": "user-001",
  "title": "Họp dự án",
  "description": "Tổng kết sprint",
  "startTime": "2026-10-12T14:00:00+07:00",
  "endTime": "2026-10-12T15:00:00+07:00",
  "attendees": 4,
  "roomId": "ROOM-A",
  "participantIds": ["user-002", "user-003"],
  "recurring": false
}
```

Chạy bằng Java 21, Spring Boot 4.0.8 và Maven. Ứng dụng dùng database MySQL riêng `meeting_management_us25` và mở tại `http://localhost:8094`.

## Bật chế độ AI

API key chỉ được đọc từ biến môi trường, không ghi trực tiếp vào source code:

```powershell
$env:GEMINI_API_KEY="api-key-cua-ban"
$env:GEMINI_MODEL="gemini-2.5-flash"
& "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.1\plugins\maven\lib\maven3\bin\mvn.cmd" spring-boot:run
```

Khi có API key, người dùng có thể nhập một câu tự nhiên như: `Tôi là user-001, đặt phòng cho 8 người lúc 14 giờ ngày mai trong 1 tiếng, hệ thống tự chọn phòng`. Backend gọi Gemini Generate Content API và dùng Structured Outputs để trích xuất dữ liệu có cấu trúc. AI không được ghi cơ sở dữ liệu; việc kiểm tra người dùng, phòng trống và tạo lịch vẫn do `ChatbotBookingService` thực hiện sau khi người dùng xác nhận.

Nếu không có API key, key không hợp lệ hoặc API tạm thời lỗi, hệ thống tự dùng lại chế độ hỏi từng bước. Có thể đổi model qua `GEMINI_MODEL` và endpoint qua `GEMINI_BASE_URL`.

## Dữ liệu mẫu để chạy thử

Dữ liệu mẫu được tự động thêm khi ứng dụng khởi động và không bị tạo trùng:

- Người tổ chức: `user-001` (Nguyễn Văn An).
- Người tham dự: `user-002`, `user-003`, `user-004`.
- Tài khoản khóa để thử lỗi: `user-locked`.
- Phòng: `ROOM-A` (6 người), `ROOM-B` (12 người), `ROOM-C` (30 người), `ROOM-D` (100 người/online).
- `ROOM-A` có lịch mẫu từ 09:00 đến 10:00 ngày kế tiếp để thử chức năng phát hiện trùng lịch.

Không cần Gemini API key để thử chế độ hỏi từng bước. Mở `http://localhost:8094`, nhập `user-001`, sau đó dùng `user-002,user-003` ở bước người tham dự. Có thể tắt hoàn toàn dữ liệu mẫu bằng biến môi trường `DEMO_DATA_ENABLED=false`.
