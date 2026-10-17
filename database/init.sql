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
    room VARCHAR(120) NULL,
    recurrence_rule VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_meeting_time CHECK (end_time > start_time),
    INDEX idx_meetings_organizer (organizer_id),
    INDEX idx_meetings_start_time (start_time),
    INDEX idx_meetings_status (status)
);

CREATE TABLE IF NOT EXISTS meeting_participants (
    meeting_id BIGINT NOT NULL, user_id VARCHAR(50) NOT NULL,
    PRIMARY KEY (meeting_id,user_id), FOREIGN KEY (meeting_id) REFERENCES meetings(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS meeting_equipment (
    meeting_id BIGINT NOT NULL, equipment_type VARCHAR(100) NOT NULL, quantity INT NOT NULL,
    FOREIGN KEY (meeting_id) REFERENCES meetings(id) ON DELETE CASCADE,
    CONSTRAINT chk_meeting_equipment_quantity CHECK (quantity > 0)
);
