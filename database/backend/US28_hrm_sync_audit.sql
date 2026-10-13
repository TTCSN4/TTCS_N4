-- Apply once to the existing ERD MySQL database.
CREATE UNIQUE INDEX uk_user_hrm_code ON `user` (hrm_code);
CREATE TABLE IF NOT EXISTS hrm_sync_runs (
    sync_run_id VARCHAR(36) NOT NULL PRIMARY KEY,
    source VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at DATETIME NOT NULL,
    finished_at DATETIME,
    departments_created INT NOT NULL DEFAULT 0,
    departments_updated INT NOT NULL DEFAULT 0,
    users_created INT NOT NULL DEFAULT 0,
    users_updated INT NOT NULL DEFAULT 0,
    users_deactivated INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000)
);
