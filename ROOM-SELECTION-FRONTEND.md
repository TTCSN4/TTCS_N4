# Danh sách phòng trống – demo frontend

Phạm vi: dùng lại danh sách phòng, trạng thái và form tạo lịch của Sprint 2. Không thêm API/backend; không sửa chức năng người tham gia.

## Cách demo

Mở `src/main/resources/templates/dashboard.html` hoặc `rooms.html` bằng Live Server. Có thể dùng các trang Thymeleaf khi ứng dụng đang chạy, nhưng phần kiểm tra/chọn phòng không cần API.

1. Chọn ngày bất kỳ, khung giờ 09:00–10:00 và 8 người: A1 bận, B2 trống đủ chỗ; C1 không đủ sức chứa.
2. Chọn 10:00–11:00: A1 trống vì hai khung giờ liền nhau không trùng. Phòng được xếp theo sức chứa tăng dần.
3. Trong danh sách phòng, thử tìm tên/vị trí, sức chứa, thiết bị và trạng thái Còn trống/Đã được đặt. Nhấn Xóa bộ lọc để xem lại.
4. Nhấn Chọn phòng: chuyển phòng, ngày, giờ và số người sang `create-meeting.html`. Form kiểm tra lại trạng thái.
5. Nhập tên cuộc họp, chọn ngày giờ trong tương lai và tạo lịch thử. Lịch được lưu trên trình duyệt, không đặt phòng/thiết bị thật, không gửi email. Quay lại kiểm tra cùng phòng và giờ để thấy phòng bận.
6. Thử số người 0, âm, thập phân hoặc trên 200; đổi giờ khi đang kiểm tra; lọc không có kết quả. Không cho chọn phòng khi thông tin chưa hợp lệ hoặc kết quả đã cũ.

## Dữ liệu và logic dùng chung

`room-availability.js` dùng `meetingRooms` của Sprint 2; chưa có dữ liệu thì dùng ba phòng mẫu. Lịch mẫu A1 09:00–10:00 và B2 14:00–15:00 lặp trên ngày được chọn để demo; có ghi rõ trên giao diện. Lịch tạo thử lưu riêng tại `meetingRoomBookings.demo.v1`. Xung đột tính theo khoảng `[bắt đầu, kết thúc)` và bỏ qua lịch đã hủy. Tất cả thời gian là UTC+7.

Dashboard, rooms và form tạo họp dùng chung kiểm tra ngày giờ, danh sách phòng và tính phòng bận. Nút chọn truyền query string và kiểm tra lại sức chứa. Khi đổi khung giờ hoặc dữ liệu ở tab khác, kết quả cũ mất hiệu lực. Dữ liệu lịch hỏng hoặc lỗi quyền lưu trữ hiển thị lỗi và không coi phòng là trống.

Các nhánh API cũ của form chỉnh sửa cuộc họp và các chức năng khác được giữ nguyên; chúng nằm ngoài phạm vi demo chọn phòng này.

## Tệp thay đổi

- `templates/rooms.html`, `templates/dashboard.html`, `templates/create-meeting.html`.
- `static/js/room-availability.js`, `static/js/dashboard-rooms.js`, `static/js/rooms.js`, `static/js/create-meeting.js`.
- `static/css/dashboard-rooms.css`, `static/css/room-selection.css`.

Đã kiểm tra cú pháp JS và luồng bằng jsdom: phòng bận/trống, bộ lọc, ngày giờ/số người không hợp lệ, đổi giờ, dữ liệu demo lỗi, điền sẵn form, lưu lịch thử và tính lại xung đột. Chưa kiểm tra hiển thị trực quan bằng trình duyệt; chưa cần kiểm thử backend cho phạm vi frontend.
