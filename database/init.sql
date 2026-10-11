CREATE DATABASE IF NOT EXISTS meeting_management
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE meeting_management;

CREATE TABLE IF NOT EXISTS meetings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    start_time DATETIME(6) NOT NULL,
    end_time DATETIME(6) NOT NULL,
    organizer_id VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    recurrence_series_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_meeting_time CHECK (end_time > start_time),
    INDEX idx_meetings_organizer (organizer_id),
    INDEX idx_meetings_start_time (start_time),
    INDEX idx_meetings_status (status)
);

INSERT INTO meetings (title, description, start_time, end_time, organizer_id, status,
                       recurrence_series_id, created_at, updated_at)
SELECT 'Daily Meeting', 'Họp cập nhật công việc hằng ngày',
       '2030-09-26 09:00:00', '2030-09-26 10:00:00',
       'NV001', 'SCHEDULED', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM meetings WHERE title = 'Daily Meeting');

INSERT INTO meetings (title, description, start_time, end_time, organizer_id, status,
                       recurrence_series_id, created_at, updated_at)
SELECT 'Review Sprint', 'Đánh giá kết quả Sprint',
       '2030-09-26 14:00:00', '2030-09-26 15:00:00',
       'NV002', 'SCHEDULED', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM meetings WHERE title = 'Review Sprint');
