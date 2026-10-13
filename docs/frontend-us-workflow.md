# Một project, mỗi US một nhánh frontend

Repository triển khai duy nhất: `TTCS_N4`. `develop` là nhánh tích hợp/default. Không đặt thêm các project Maven hoặc repository Git lồng bên trong project này.

## Cấu trúc

```text
src/main/resources/static/
  index.html                         # trang chủ hiện có trên develop
  create-meeting.html                # giao diện tạo họp hiện có
  meeting-participants.html          # US04
  room-booking.html                  # US08
  ...                               # một HTML riêng cho mỗi US
  css/
    meeting-ui-common.css            # giao diện dùng chung
    meeting-participants.css         # chỉ ghi override của US04
    room-booking.css                 # chỉ ghi override của US08
  js/
    meeting-ui-common.js             # shell, helpers và dữ liệu demo chung
    meeting-participants.js          # chỉ nghiệp vụ giao diện US04
    room-booking.js                  # chỉ nghiệp vụ giao diện US08
```

Một tên file có ý nghĩa nghiệp vụ giúp tránh nhiều người sửa cùng `index.html`, `app.js`, `styles.css`. Đổi tên không loại bỏ mọi conflict: phần common vẫn cần phối hợp và được cập nhật qua PR riêng.

Trang chủ, code Java, cấu hình database, Maven và giao diện tạo họp đang có trên `develop` được giữ nguyên. Các màn hình mới là giao diện demo có localStorage; chưa tự động trở thành client của API backend đang có. Trang US14 dùng API cũ được giữ riêng tại `equipment-management-api.html`.

## Nhánh nền tảng và nhánh tích hợp xem thử

- `chore/frontend-us-foundation`: CSS/JS common, bảng ánh xạ nhánh và công cụ kiểm thử. **Merge PR này vào develop trước** để các US dùng chung nền tảng.
- `feature/USxx-...-frontend`: mỗi nhánh thêm HTML/JS/CSS của đúng một US. Tên lấy từ bảng phân công trong `US_list.txt`; US08, US09, US14 dùng tên nhánh đã có trong Git vì chưa có trong bảng phân công.
- `integration/frontend-us-preview`: bản ghép cả 12 US để chạy kiểm thử. Không dùng thay cho các PR riêng và không phát triển từng US trên nhánh này.

Các nhánh US đã tồn tại trên origin được giữ lịch sử và tích hợp nhánh nền tảng bằng merge. Nếu index.html cũ dùng riêng cho một US bị conflict, giữ trang chủ develop; chức năng US chuyển sang trang có tên riêng. Không force-push hoặc viết lại lịch sử nhánh remote.

Khi common chưa merge vào develop, feature PR cũng hiển thị thay đổi common. Sau khi common merge, GitHub sẽ chỉ hiển thị phần thay đổi chưa có của US. Sidebar có các liên kết tới cả 12 trang; nhánh riêng chưa ghép các US khác sẽ chưa có các trang đó. Dùng nhánh integration để xem toàn bộ.

Chi tiết US, tên trang và tên nhánh: [frontend-us-branches.json](frontend-us-branches.json).

## Push và tạo PR

Chạy từ thư mục `TTCS_N4`:

```powershell
git switch chore/frontend-us-foundation
git push -u origin chore/frontend-us-foundation
# Tạo PR: base develop, compare chore/frontend-us-foundation.

# Sau đó, ví dụ US04:
git switch feature/US04-meeting-participants-frontend
git status
git push -u origin feature/US04-meeting-participants-frontend
# Tạo PR: base develop, compare feature/US04-meeting-participants-frontend.
```

Các nhánh mới trong đợt tổ chức này chỉ được tạo và commit local; chưa push tự động.

Sau khi PR nền tảng được merge, cập nhật nhánh feature mà không rewrite lịch sử:

```powershell
git fetch origin
git switch feature/US04-meeting-participants-frontend
git merge origin/develop
```

Khi chỉnh sửa một US, stage đúng các file liên quan. Ví dụ:

```powershell
git add src/main/resources/static/meeting-participants.html
git add src/main/resources/static/js/meeting-participants.js
git add src/main/resources/static/css/meeting-participants.css
git diff --cached
git commit -m "feat(US04): update participant invitation UI"
git push
```

## Chạy và kiểm thử

Chạy project Spring Boot bình thường, sau đó mở `/meeting-participants.html`, `/room-booking.html`, v.v. Mọi US dùng cùng origin nên dữ liệu minh họa chia sẻ trong một trình duyệt.

Kiểm tra frontend tĩnh, không cần backend:

```powershell
git switch integration/frontend-us-preview
npm.cmd install --prefix tools/frontend
node tools/frontend/preview.cjs
# Terminal thứ hai:
node tools/frontend/test-ui.cjs
```

Mở `http://127.0.0.1:4174/meeting-participants.html`. Test dùng Microsoft Edge headless. Ảnh và kết quả lưu ở `target/frontend-ui-results/`, được Git bỏ qua.

Kiểm tra riêng một nhánh US:

```powershell
$env:US_STORY='US04'
node tools/frontend/test-ui.cjs
Remove-Item Env:US_STORY
```

Để kiểm tra trên ứng dụng Spring Boot đang chạy thay cho server tĩnh, đặt `$env:BASE_URL='http://127.0.0.1:8080'`.

Giữ các thư mục US cũ ngoài `TTCS_N4` để đối chiếu. Code tiếp theo nên sửa trong `TTCS_N4` theo nhánh; không chạy sync.cjs của cấu trúc cũ để chép đè project chung.
