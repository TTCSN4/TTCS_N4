# Quản lý cuộc họp

Ứng dụng sử dụng Java 21, Spring Boot 4.0.8, Spring Data JPA, Flyway, MySQL và Maven. Giao diện kiểm thử nằm trong `src/main/resources/static/`.

## Yêu cầu môi trường

- JDK 21
- Maven 3.9 trở lên
- MySQL 8 trở lên nếu chạy bằng cơ sở dữ liệu MySQL

Kiểm tra phiên bản Java và Maven:

```bash
java -version
mvn -version
```

Các lệnh bên dưới cần được chạy tại thư mục chứa file `pom.xml`.

## Chạy nhanh bằng H2

Đây là cách đơn giản nhất để chạy và kiểm thử ứng dụng. Bạn không cần cài đặt MySQL. Hồ sơ `demo` sử dụng H2 và lưu dữ liệu tại `data/meeting-demo`:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

Sau khi cửa sổ lệnh hiển thị `Started MeetingmanagementApplication`, mở:

- Giao diện chính: http://localhost:8091/
- Trang quản lý H2: http://localhost:8091/h2-console
- API danh sách người tham dự: http://localhost:8091/api/participants

Thông tin đăng nhập trang quản lý H2:

```text
Đường dẫn JDBC: jdbc:h2:file:./data/meeting-demo
Tên đăng nhập: sa
Mật khẩu: để trống
```

Nhấn `Ctrl+C` trong cửa sổ lệnh để dừng ứng dụng.

### Trường hợp cổng 8091 đang được sử dụng

Chạy ứng dụng trên cổng 8092:

```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=demo" "-Dspring-boot.run.arguments=--server.port=8092"
```

Sau đó mở http://localhost:8092/.

## Chạy bằng MySQL

Khởi động MySQL và bảo đảm tài khoản có quyền tạo cơ sở dữ liệu. Cấu hình mặc định của ứng dụng:

```text
Máy chủ: localhost:3306
Cơ sở dữ liệu: meeting_management
Tên đăng nhập: root
Mật khẩu: root
```

Chạy ứng dụng:

```bash
mvn spring-boot:run
```

Flyway sẽ tự động tạo bảng và dữ liệu mẫu trong lần chạy đầu tiên. Sau khi ứng dụng khởi động, mở http://localhost:8091/.

Có thể thay đổi thông tin kết nối bằng biến môi trường. Ví dụ với PowerShell:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/meeting_management?createDatabaseIfNotExist=true&serverTimezone=UTC"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "mat-khau-cua-ban"
$env:PORT = "8091"
mvn spring-boot:run
```

## Chạy bằng IntelliJ IDEA

1. Mở file `src/main/java/com/ttcs/meetingmanagement/MeetingmanagementApplication.java`.
2. Chọn JDK 21 cho dự án.
3. Tạo cấu hình chạy Spring Boot.
4. Để chạy bằng H2, thêm đối số chương trình sau:

```text
--spring.profiles.active=demo
```

5. Nhấn **Run** để khởi động ứng dụng.

## Đóng gói và chạy file JAR

```bash
mvn clean package
java -jar target/meetingmanagement-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo
```

## Chạy kiểm thử tự động

```bash
mvn test
```

## API kiểm tra lịch rảnh

```http
GET /api/participants
POST /api/availability
Content-Type: application/json

{"date":"2026-09-26","participantIds":[1,2,3],"durationMinutes":60}
```

API tìm các khoảng trống trong giờ làm việc từ 08:00 đến 17:00 sau khi loại bỏ những cuộc họp trùng với lịch của người tham dự đã chọn.
