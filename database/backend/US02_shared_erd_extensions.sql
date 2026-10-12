-- Apply once to an existing MySQL meeting_management database before using
-- the backend branches with the mysql profile (Hibernate validate).
-- Core ERD tables must already exist; this does not replace existing data.
ALTER TABLE `user` ADD COLUMN password_hash VARCHAR(255) NULL;
CREATE UNIQUE INDEX uk_user_email ON `user` (email);
CREATE TABLE IF NOT EXISTS department (
    department_id VARCHAR(50) NOT NULL PRIMARY KEY,
    department_name VARCHAR(255),
    hrm_code VARCHAR(255),
    last_synced_at DATETIME,
    CONSTRAINT uk_department_hrm_code UNIQUE (hrm_code)
);
CREATE TABLE IF NOT EXISTS meeting_recurrence (
    id VARCHAR(50) NOT NULL PRIMARY KEY,
    configuration VARCHAR(4000) NOT NULL,
    occurrences INT NOT NULL
);
