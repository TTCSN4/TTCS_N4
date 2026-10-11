# Meeting Management

Module Spring Boot cho phép nhân viên xem, chỉnh sửa và hủy lịch họp. Backend kiểm tra người thao tác có phải người tổ chức và cuộc họp còn ở trạng thái cho phép trước khi cập nhật.

## Công nghệ

- Java 21
- Spring Boot 3.3.4
- Spring Web, Spring Data JPA và Bean Validation
- MySQL 8
- HTML, CSS và JavaScript
- Maven

## Quy tắc nghiệp vụ

- Chỉ nhân viên có mã trùng `organizer_id` được sửa hoặc hủy cuộc họp.
- Mã nhân viên gồm chữ và số thứ tự, ví dụ `NV001`.
- Chỉ cuộc họp `SCHEDULED` và chưa bắt đầu được thao tác.
- Hủy là thao tác mềm: trạng thái chuyển thành `CANCELLED`.
- Thời gian kết thúc phải sau thời gian bắt đầu.
- Backend khóa bản ghi trong transaction để tránh hai yêu cầu cập nhật đồng thời.

## Chuẩn bị MySQL

Chạy script `database/init.sql` bằng MySQL Workbench hoặc dòng lệnh:

```bash
mysql -u root -p < database/init.sql
```

Script tạo database `meeting_management`, bảng `meetings` và hai cuộc họp mẫu trong tương lai. Nếu không chạy script, Hibernate vẫn có thể tự tạo database và bảng nhưng chưa có dữ liệu mẫu.

## Cấu hình và chạy

Thiết lập biến môi trường:

```text
DB_URL=jdbc:mysql://localhost:3306/meeting_management?createDatabaseIfNotExist=true&serverTimezone=UTC
DB_USERNAME=root
DB_PASSWORD=your_password
```

Ví dụ PowerShell:

```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_password"
mvn spring-boot:run
```

Mở giao diện tại `http://localhost:8080/`.

## API

```http
GET /api/meetings
GET /api/meetings/{meetingId}
```

Chỉnh sửa:

```http
PUT /api/meetings/1
X-User-Id: NV001
Content-Type: application/json

{
  "title": "Sprint planning",
  "description": "Planning for Sprint 2",
  "startTime": "2030-10-01T09:00:00+07:00",
  "endTime": "2030-10-01T10:00:00+07:00"
}
```

Hủy:

```http
DELETE /api/meetings/1
X-User-Id: NV001
```

## Mã lỗi

- `400 Bad Request`: ID, mã nhân viên hoặc dữ liệu nhập không hợp lệ.
- `403 Forbidden`: nhân viên không phải người tổ chức.
- `404 Not Found`: cuộc họp không tồn tại.
- `409 Conflict`: cuộc họp đã bắt đầu hoặc không còn `SCHEDULED`.

## Kiểm thử

```bash
mvn clean test
```

Bộ test bao phủ luồng thành công, kiểm tra quyền, trạng thái, thời gian và mã lỗi HTTP.

## Cấu trúc chính

```text
src/main/java/                   Backend Spring Boot
src/main/resources/static/       Giao diện HTML CSS JavaScript
src/test/java/                   Unit test và controller test
database/init.sql                Khởi tạo MySQL và dữ liệu mẫu
pom.xml                          Cấu hình Maven và Java 21
```

`X-User-Id` hiện mô phỏng mã nhân viên đã xác thực. Khi tích hợp đăng nhập, mã này nên được lấy từ token hoặc security context thay vì nhận trực tiếp từ client công khai.
