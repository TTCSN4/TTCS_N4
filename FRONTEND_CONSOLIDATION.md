# Hợp nhất frontend vào project TTCS_N4

Ngày hoàn thành: 13/10/2026. Project duy nhất: C:\Giaodien\TTCS_N4.
Repository của nhóm: https://github.com/TTCSN4/TTCS_N4.git.

**Đã commit local 10 nhánh frontend. Chưa push và chưa merge vào develop thật, theo lựa chọn trước đó của bạn.**

| US | Nhánh local | Commit | Đường dẫn giao diện chính khi phục vụ static |
| --- | --- | --- | --- |
| US01 | feature/US01-create-meeting-frontend | 03dd53b | /us01/templates/create-meeting.html |
| US03 | feature/US03-recurring-meeting-frontend | e642f15 | /us03/create-meeting.html |
| US06 | feature/US06-meeting-history-frontend | e285fd9 | /us06/templates/meeting-history.html |
| US07 | feature/US07-available-rooms-frontend | 996c1cf | /us07/templates/rooms.html |
| US17 | feature/US17-mobile-meeting-calendar-frontend | a86a23c | /us17/mobile-meetings.html |
| US18 | feature/US18-create-user-frontend | 6fbedb4 | /us18/index.html |
| US21 | feature/US21-room-booking-permission-frontend | 260e8f0 | /us21/templates/room-permissions.html |
| US22 | feature/US22-room-usage-report-frontend | 97413d8 | /us22/templates/room-usage-report.html |
| US27 | feature/US27-qr-checkin-checkout-frontend | 2ecf3c2 | /us27/templates/qr-attendance.html |
| US28 | feature/US28-hrm-erp-integration-frontend | b13725f | /us28/templates/hrm-integration.html |

## Nội dung đã hợp nhất

Đã xử lý 14 thư mục nguồn: US01, US01_1, US03, US06, US06_1, US07, US17, us18, US18_1, US21, US22, US22_1, US27, US28. Các nguồn được đưa vào 10 nhánh theo US, không tạo repository mới trong C:\Giaodien.

Mỗi nhánh thêm giao diện vào src/main/resources/static/usxx/. HTML, CSS, JavaScript, ảnh và manifest PWA được giữ cùng module; tên file bên trong được giữ và sửa các đường dẫn tham chiếu phù hợp. Các file có tên giống nhau giữa các US không ghi đè nhau.

US01 và US01_1 có giao diện giống hệt nên dùng chung một bản. Các bản khác nội dung của US06, US18, US22 được giữ trong variants của đúng US, gồm cả frontend ở US06/src và us18/US18-upload/TTCS_N4.

- US06: src/main/resources/static/us06/variants/workspace/, src/main/resources/static/us06/variants/us06_1/.
- US18: src/main/resources/static/us18/variants/us18-upload/, src/main/resources/static/us18/variants/us18_1/.
- US22: src/main/resources/static/us22/variants/us22_1/.

US07 rỗng tại thời điểm xử lý; đã phục hồi giao diện từ ref origin/feature/US07-available-rooms-frontend còn lưu trong repository local cũ. Các stylesheet bị thiếu được phục hồi từ lịch sử Git. Trang create-meeting của US21 đã được giữ từ bản nguồn đọc lúc đầu; sau đó file không còn trong thư mục nguồn trước lúc sao lưu, và phiên bản Git gốc cũng được lưu trong lịch sử nguồn.

US07, US27, US28 hiện không có nhánh frontend trên remote. Đã tạo đúng tên nhánh frontend local để bạn push với -u. Các nhánh frontend còn lại theo dõi origin tương ứng.

Nền: origin/develop tại bacb9d0. Nền chung fa7a740 nối lịch sử các source clone và nhánh frontend cũ, với cây file giống hệt develop. Điều này tránh conflict rename khi nhiều US dùng chung template cũ. Mỗi commit riêng chỉ thêm frontend trong namespace của US đó. Các nhánh giữ lịch sử origin làm tổ tiên và có thể push thường ở trạng thái đã kiểm tra.

Kết quả merge thử: 115 file frontend mới. Backend, cấu hình build, database và các file dùng chung đã có của develop không thay đổi. Không nhập Java, database demo hoặc project Maven riêng từ các thư mục US.

## Kiểm tra

- 46 trang HTML và 37 script được kiểm tra bằng JSDOM/Node; không thiếu link/tài nguyên và không có lỗi thực thi trong các luồng đã chạy.
- Kiểm tra danh sách/chi tiết US17, chuyển tab quyền US21, check-in/check-out và từ chối check-in trùng của US27 với dữ liệu demo.
- Kiểm tra manifest, kích thước icon và phạm vi cache service worker US17; không chặn API hoặc dọn cache của ứng dụng khác.
- Merge thử cả 10 nhánh theo thứ tự xuôi và ngược: không conflict, cùng cây file kết quả. Git diff --check đạt; không có diff ngoài các thư mục frontend US.
- Chưa kiểm tra trực quan bằng trình duyệt thật, camera thật, cài PWA hoặc backend thật. Một số giao diện vẫn là demo; giữ nguyên cách hoạt động của bản nguồn.

## Sao lưu và cây thư mục

14 thư mục US riêng đã được dọn sau khi xác minh nội dung nằm trong các nhánh và có bản sao lưu. C:\Giaodien chỉ còn một thư mục project TTCS_N4; backlogs.txt, ERD_code.txt, tài liệu yêu cầu và metadata Git ở thư mục cha được giữ.

Bản sao đầy đủ các thư mục US, gồm file chưa commit, thư mục rỗng, backend cũ và toàn bộ .git của các clone:

TTCS_N4/.git/frontend-import-backups/20261013/us-sources.zip

Đã xác minh SHA-256 của tất cả 1.554 file trong ZIP. Backup không nằm trong cây file được commit/push. Ref nguồn cũng được giữ dưới refs/archive/frontend-consolidation-20261013/. Manifest và bằng chứng kiểm tra nằm cùng thư mục backup.

Các worktree kiểm tra tạm đã được dọn. develop local vẫn giữ nguyên commit và project vẫn đang ở develop, nên cần switch nhánh để xem giao diện của US.

## Mở nhánh và push

```powershell
cd C:\Giaodien\TTCS_N4
git switch feature/US21-room-booking-permission-frontend
git status
git push -u origin feature/US21-room-booking-permission-frontend
```

Thay tên nhánh bằng nhánh cần làm trong bảng. Ví dụ US21 có file src/main/resources/static/us21/templates/room-permissions.html. Nếu dùng Spring Boot phục vụ static, mở /us21/templates/room-permissions.html.

Sau khi push, tạo pull request từng nhánh vào develop để nhóm duyệt. Nếu được phép merge trực tiếp:

```powershell
git fetch origin
git switch develop
git merge --ff-only origin/develop
git merge --no-ff feature/US01-create-meeting-frontend
git merge --no-ff feature/US03-recurring-meeting-frontend
git merge --no-ff feature/US06-meeting-history-frontend
git merge --no-ff feature/US07-available-rooms-frontend
git merge --no-ff feature/US17-mobile-meeting-calendar-frontend
git merge --no-ff feature/US18-create-user-frontend
git merge --no-ff feature/US21-room-booking-permission-frontend
git merge --no-ff feature/US22-room-usage-report-frontend
git merge --no-ff feature/US27-qr-checkin-checkout-frontend
git merge --no-ff feature/US28-hrm-erp-integration-frontend
git push origin develop
```

Nếu origin/develop hoặc nhánh frontend có commit mới sau thời điểm kiểm tra, cần fetch và kiểm tra lại trước khi push/merge. Không dùng force-push.

## Cập nhật giao diện US07 ngày 13/10/2026

Commit 996c1cf làm lại danh sách phòng trống theo mẫu US21/US28: lọc ngày/giờ, số người, vị trí, thiết bị; xem chi tiết; kiểm tra lại trước khi chuyển sang tạo lịch. Chrome đã kiểm tra luồng chọn phòng và bố cục 1440/768/390px. Bản hiện tại sử dụng dữ liệu minh họa. Chưa push.
