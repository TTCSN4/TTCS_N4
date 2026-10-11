# US28 – Tích hợp HRM/ERP

US28 đồng bộ phòng ban và nhân viên từ HRM/ERP vào hệ thống quản lý phòng họp. Dữ liệu HRM/ERP là nguồn chính cho mã nhân viên, họ tên, email, số điện thoại, phòng ban, vai trò và trạng thái làm việc.

## Luồng hoạt động

- Hệ thống tự kéo snapshot từ HRM/ERP theo chu kỳ cấu hình.
- Quản trị viên có thể bấm **Đồng bộ ngay** trên trang chủ.
- HRM/ERP có thể đẩy dữ liệu tới webhook khi có thay đổi.
- Mã phòng ban và mã nhân viên được chuẩn hóa chữ hoa rồi upsert, không tạo bản ghi trùng.
- Snapshot đầy đủ sẽ chuyển nhân viên không còn xuất hiện sang `INACTIVE`; webhook tăng dần không làm vậy.
- Mỗi lần đồng bộ được ghi vào `hrm_sync_runs` với trạng thái `RUNNING`, `SUCCESS` hoặc `FAILED`.
- Lỗi không làm mất dữ liệu đã đồng bộ thành công trước đó; lịch chạy kế tiếp sẽ thử lại.

## Cấu hình

```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="123456"
$env:HRM_INTEGRATION_ENABLED="true"
$env:HRM_BASE_URL="https://hrm.example.com"
$env:HRM_SNAPSHOT_PATH="/api/v1/workforce/snapshot"
$env:HRM_API_KEY="api-key-do-hrm-cap"
$env:HRM_WEBHOOK_KEY="webhook-key-dung-chung"
mvn spring-boot:run
```

Mặc định ứng dụng dùng database dùng chung `meeting_management`, cổng `8080` và chạy đồng bộ mỗi 15 phút. Có thể đổi cổng bằng biến môi trường `PORT`. Kết nối tới HRM có timeout mặc định 5 giây và timeout đọc 30 giây (có thể đổi bằng `HRM_CONNECT_TIMEOUT_MS`, `HRM_READ_TIMEOUT_MS`). Khi chưa có HRM/ERP thật, giữ `HRM_INTEGRATION_ENABLED=false`; trang trạng thái vẫn mở được nhưng nút kéo dữ liệu sẽ báo tích hợp chưa bật. Hai endpoint nhận dữ liệu sẽ từ chối hoạt động nếu chưa cấu hình `HRM_WEBHOOK_KEY`.

## Hợp đồng dữ liệu HRM/ERP

Endpoint snapshot phải trả JSON dạng:

```json
{
  "source": "HRM-ERP",
  "sourceUpdatedAt": "2026-10-11T10:30:00+07:00",
  "fullSnapshot": true,
  "departments": [
    { "hrmCode": "IT", "name": "Công nghệ thông tin" }
  ],
  "employees": [
    {
      "hrmCode": "NV001",
      "fullName": "Nguyễn Văn A",
      "email": "a@company.vn",
      "phone": "0900000001",
      "departmentHrmCode": "IT",
      "status": "ACTIVE",
      "roleCode": "ATTENDEE"
    }
  ]
}
```

Trạng thái nhân viên hợp lệ: `ACTIVE`, `INACTIVE`, `SUSPENDED`, `TERMINATED`. Vai trò mặc định là `ATTENDEE`; các vai trò dựng sẵn gồm `ATTENDEE`, `ORGANIZER`, `ADMIN`.

## API vận hành

- `POST /api/integrations/hrm/pull`: kéo dữ liệu từ HRM/ERP ngay lập tức.
- `POST /api/integrations/hrm/webhook`: nhận thay đổi do HRM/ERP đẩy sang; gửi header `X-Integration-Key`.
- `POST /api/integrations/hrm/sync`: endpoint tương thích dữ liệu snapshot cũ, cũng được bảo vệ bằng integration key.
- `GET /api/integrations/hrm/status`: trạng thái tích hợp và lần đồng bộ gần nhất.
- `GET /api/integrations/hrm/history?limit=20`: lịch sử đồng bộ.
- `GET /api/integrations/hrm/users`: dữ liệu nhân viên đã đồng bộ.
- `GET /api/integrations/hrm/departments`: dữ liệu phòng ban đã đồng bộ.

Mở `http://localhost:8080` để xem trạng thái và thực hiện đồng bộ thủ công.

## Chạy kiểm thử

```powershell
cd US28
mvn test
```

Kiểm thử dùng H2 độc lập, không gọi HRM/ERP thật và không ghi vào MySQL.
