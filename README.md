# Meeting Management API

Spring Boot 3 / Java 17 API for editing and cancelling meetings stored in MySQL.

## Business rules

- Only the meeting organizer (`organizer_id`) may edit or cancel a meeting.
- Only a future meeting with status `SCHEDULED` may be edited or cancelled.
- Cancelling is a soft delete: status changes to `CANCELLED`.
- Edit data must contain a title and future start/end times; end time must be after start time.
- Rows are locked while an edit/cancel transaction is running to avoid concurrent updates.

## Run with MySQL

Create a MySQL user if needed, then set these environment variables:

```text
DB_URL=jdbc:mysql://localhost:3306/meeting_management?createDatabaseIfNotExist=true&serverTimezone=UTC
DB_USERNAME=root
DB_PASSWORD=your_password
```

Run the application with Maven:

```text
mvn spring-boot:run
```

Hibernate creates or updates the `meetings` table from the `Meeting` entity.
The meeting management interface is available at `http://localhost:8080/`.

## API

Until the authentication module is integrated, the authenticated user ID is passed in the required `X-User-Id` header. A production gateway or security filter must set this header; it must not be accepted directly from an untrusted public client.

### Edit a meeting

```http
PUT /api/meetings/1
X-User-Id: NV001
Content-Type: application/json

{
  "title": "Sprint planning",
  "description": "Planning for Sprint 2",
  "startTime": "2026-10-01T09:00:00+07:00",
  "endTime": "2026-10-01T10:00:00+07:00"
}
```

Returns `200 OK` with the updated meeting.

### Cancel a meeting

```http
DELETE /api/meetings/1
X-User-Id: NV001
```

Returns `204 No Content` and stores status `CANCELLED`.

## Error responses

- `400 Bad Request`: invalid ID, missing/invalid fields, invalid time range.
- `403 Forbidden`: the user is not the organizer.
- `404 Not Found`: the meeting does not exist.
- `409 Conflict`: the meeting is no longer scheduled or has already started.
