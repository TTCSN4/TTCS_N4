CREATE DATABASE IF NOT EXISTS meeting_management
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE meeting_management;

CREATE TABLE IF NOT EXISTS meeting (
    meeting_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    start_time DATETIME(6) NOT NULL,
    end_time DATETIME(6) NOT NULL,
    organizer_id VARCHAR(255) NOT NULL,
    room_id VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL,
    is_recurring BOOLEAN,
    recurrence_series_id VARCHAR(255) NULL,
    recurrence_rule VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    cancel_reason TEXT NULL,
    cancelled_at DATETIME(6) NULL,
    PRIMARY KEY (meeting_id),
    CONSTRAINT chk_meeting_time CHECK (end_time > start_time),
    INDEX idx_meetings_organizer (organizer_id),
    INDEX idx_meetings_start_time (start_time),
    INDEX idx_meetings_status (status)
);

CREATE TABLE IF NOT EXISTS meeting_participant (
    participant_id VARCHAR(255) NOT NULL, meeting_id VARCHAR(255), user_id VARCHAR(255),
    status VARCHAR(255), responded_at DATETIME(6),
    PRIMARY KEY (participant_id), FOREIGN KEY (meeting_id) REFERENCES meeting(meeting_id)
);
CREATE TABLE IF NOT EXISTS meeting_equipment (
    meeting_id VARCHAR(255) NOT NULL, equipment_id VARCHAR(255) NOT NULL, quantity INT NOT NULL,
    PRIMARY KEY (meeting_id,equipment_id),
    FOREIGN KEY (meeting_id) REFERENCES meeting(meeting_id),
    FOREIGN KEY (equipment_id) REFERENCES equipment(equipment_id),
    CONSTRAINT chk_meeting_equipment_quantity CHECK (quantity > 0)
);
