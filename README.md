# TTCS — Project hợp nhất

Đây là một repository chứa mã Java, toàn bộ giao diện Meeting và bộ chạy web.
Giao diện hiện tại giữ nguyên 27 chức năng theo bộ ảnh tham chiếu.

## Clone và chạy đúng giao diện hiện tại

Cần **Node.js 18.17 trở lên**. Tại thư mục vừa clone:

```sh
npm ci
node server.cjs
```

Mở **http://127.0.0.1:8090**. Có thể dùng `npm start` thay cho lệnh Node.
Không cần chép thêm thư mục US, `public`, file ZIP, dữ liệu hay đường dẫn của máy tác giả.

Khi khởi tạo lần đầu, cửa sổ máy chủ hiện mật khẩu ngẫu nhiên của `admin`,
`pm`, `member`. Lưu lại để đăng nhập. Dữ liệu và mật khẩu đã băm được lưu ở
`data/web-state.json`; thư mục này được Git bỏ qua. Người clone có dữ liệu mẫu
và mật khẩu riêng, không nhận dữ liệu hoặc tài khoản cá nhân của người push.
Tài khoản tạo qua US18 đăng nhập được bằng email và mật khẩu đã nhập.

## Cấu trúc mã nguồn

```text
.mvn/wrapper/                 Maven Wrapper
database/                    SQL backend và schema lưu dữ liệu web
docs/                        Tài liệu API, hướng dẫn và kết quả hợp nhất
src/
  main/java/com/ttcs/meetingmanagement/   Mã Java các nghiệp vụ
  main/resources/
    static/                  Một bản giao diện duy nhất, được chạy trực tiếp
      portal/                Điều hướng và kết nối dữ liệu dùng chung
      us01/ ... us28/        HTML, CSS, JavaScript, ảnh của từng chức năng
    application*.properties  Cấu hình Spring Boot
  test/java/                 Kiểm thử Java
tools/
  web/                       Máy chủ web/API Node đang phục vụ giao diện
  tests/                     Kiểm thử web, API, phân quyền và lưu dữ liệu
pom.xml                      Cấu hình build Java
mvnw / mvnw.cmd              Lệnh Maven cho Linux/macOS và Windows
package.json / package-lock.json
server.cjs                   Điểm chạy tương thích: node server.cjs
site.config.json             Cấu hình mặc định có thể đưa lên Git
```

`target`, `node_modules`, `data`, bản sao lưu và file `.env` là nội dung cục bộ,
không đưa lên GitHub. `target` được Maven tự sinh khi build.
Không còn một bản giao diện khác ở thư mục `public` ngoài `src`.

## Java / Spring Boot

Cần **JDK 21 trở lên**. Windows:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
.\mvnw.cmd spring-boot:run
```

Linux/macOS dùng `./mvnw` tương ứng. Backend Java khởi động ở cổng 8080,
mặc định dùng H2 để không đòi hỏi MySQL hoặc dịch vụ ngoài khi build/test.

**Để chạy web giống bản hiện tại, dùng `node server.cjs` ở cổng 8090.**
Bộ chạy này phục vụ trực tiếp `src/main/resources/static` và API trong `tools/web`.
Java và Node là hai lựa chọn backend trong cùng repository; không tự trộn dữ liệu
giữa H2, MySQL và file JSON. Chế độ proxy Java cần hợp đồng xác thực/API phù hợp;
nó không phải lệnh mặc định để mở đầy đủ bản demo hiện tại.

## MySQL đã có

Giữ nguyên các bảng và dữ liệu MySQL hiện có. Chạy `start-mysql.ps1` để dùng
bộ chạy web với các bảng `web_*`; script hỏi mật khẩu, không ghi vào mã nguồn.
Schema nằm tại `database/web-schema.sql`. Không tự chạy SQL lên database có sẵn.
Biến kết nối: `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD`, `DB_NAME`.
Profile MySQL của Java dùng `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` và kiểm tra
schema bằng `validate`.

## Kiểm tra và đưa lên GitHub

```sh
npm test
```

Kiểm thử Node dùng file tạm riêng, mở đủ 27 giao diện và tài nguyên, kiểm tra
tạo/sửa/hủy lịch, tồn kho, phân quyền, tài khoản mới, khởi động lại và lỗi ghi file.
Kiểm thử Java chạy bằng Maven Wrapper ở trên.

Repository giữ lịch sử từ nguồn US11-clean và đã hợp nhất các bổ sung US11.
Các nguồn sạch US12/US18 có mã Java trùng nhau nên không tạo thêm bản sao.
Chưa tạo commit hoặc push thay người dùng. Trước khi push, xem `git status` và
chọn nhánh đích đúng với quy trình của nhóm.

US15 chưa có giao diện nguồn. Email, AI/chatbot, HRM/ERP và QR trong bộ chạy
Node giữ phạm vi mô phỏng của phiên bản hiện tại. Xem `docs/consolidation`
để biết nguồn và kết quả kiểm tra; một số báo cáo cũ được giữ làm lịch sử.
