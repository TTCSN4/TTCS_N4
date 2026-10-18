-- Apply once to the notification table defined by ERD_code.txt.
ALTER TABLE notification
    MODIFY COLUMN type VARCHAR(50) NOT NULL,
    MODIFY COLUMN channel VARCHAR(16) NOT NULL,
    ADD COLUMN recipient_email VARCHAR(255) NULL,
    ADD COLUMN lead_minutes INT NOT NULL DEFAULT 15,
    ADD COLUMN last_attempt_at DATETIME NULL,
    ADD COLUMN next_attempt_at DATETIME NULL,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN error_message VARCHAR(1000) NULL;
CREATE UNIQUE INDEX uk_notification_meeting_user_type_time
    ON notification (meeting_id, user_id, type, channel, scheduled_at);
