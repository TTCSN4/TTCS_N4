# US17 – Lịch họp di động và PWA

## Chạy demo

Mở `src/main/resources/static/mobile-meetings.html` bằng Live Server (localhost), hoặc mở `/mobile-meetings.html` khi Spring đang chạy. Không mở bằng `file://` nếu muốn cài PWA/offline. Dữ liệu cuộc họp và cập nhật trạng thái là mô phỏng, lưu trên thiết bị; không yêu cầu MySQL hoặc API.

Trang Thymeleaf `templates/mobile-meetings.html` vẫn được giữ. Nếu xem qua thư mục templates, chọn **Mở bản ứng dụng** để sang trang tĩnh thuộc phạm vi PWA. Bản Spring hiện không có controller cho trang template này; không cần thêm Java vì trang static đã có thể được phục vụ.

## Các thao tác

1. Xem danh sách, tìm tên/phòng/người tổ chức, lọc ngày và trạng thái.
2. Chọn cuộc họp, xem thông tin, người tham gia và lịch sử trạng thái. Các mục tài liệu/ghi chú chưa có dữ liệu mẫu được ghi rõ.
3. Chọn **Cập nhật trạng thái** và một trạng thái hợp lệ, xác nhận. Có loading và kết quả được giữ sau khi tải lại.
4. Mở tài khoản demo để thử quyền chỉ xem hoặc mô phỏng cập nhật thất bại. Thất bại giữ nguyên trạng thái cũ.
5. Khi trang báo sẵn sàng offline, chuyển trình duyệt sang Offline rồi tải lại: vẫn xem danh sách/chi tiết. Cập nhật trạng thái khóa khi ngoại tuyến; không có hàng đợi đồng bộ máy chủ.
6. Cài qua **Cài ứng dụng** khi trình duyệt hỗ trợ, hoặc menu trình duyệt. Safari iOS dùng Chia sẻ → Thêm vào màn hình chính. Chỉ localhost/HTTPS hỗ trợ service worker.
7. Kiểm tra kích thước 375px, 768px và desktop. Trên điện thoại có bottom navigation, bộ lọc thu gọn, nút chạm tối thiểu 44px, vùng safe-area và dialog có cuộn.

## PWA

Manifest có tên, standalone, start URL, scope và PNG 192/512. Service worker lưu trang tĩnh, CSS, JS, manifest và icon. Cache chỉ áp dụng cho shell US17, không chặn API/trang khác; URL có query vẫn dùng được cache khi offline. Khi có phiên bản mới, đóng và mở lại ứng dụng để cập nhật. Trạng thái là dữ liệu mẫu localStorage, chưa phải đồng bộ thật.

## Tệp

- `templates/mobile-meetings.html`: trang template, accessibility và đường dẫn sang bản ứng dụng.
- `static/mobile-meetings.html`: trang tĩnh có thể cài/offline.
- `static/css/mobile-meetings.css`, `static/js/mobile-meetings.js`: responsive và các thao tác.
- `static/manifest-us17.webmanifest`, `static/sw-us17.js`: PWA.
- `static/icons/meeting-192.png`, `meeting-512.png`: icon cục bộ.

Đã kiểm tra cú pháp và hành vi bằng jsdom, kiểm tra manifest/kích thước icon, mô phỏng vòng đời/cache service worker bằng Node. Chưa xác minh trực quan, cài đặt PWA và offline bằng trình duyệt/điện thoại thật.
