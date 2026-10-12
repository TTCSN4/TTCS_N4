-- Apply once to the existing ERD MySQL database, if this association is absent.
CREATE TABLE IF NOT EXISTS meeting_equipment (
    meeting_id VARCHAR(50) NOT NULL,
    equipment_id VARCHAR(50) NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (meeting_id, equipment_id)
);
