-- READ-ONLY preflight for an existing equipment_bookings deployment.
-- Stop application writes and back up the database before any migration.
-- The new application uses the ORIGINAL ERD table meeting_equipment.
-- Do not drop equipment_bookings: retain it as an audit/rollback source.

-- These rows cannot be represented automatically by the ERD allocation model.
SELECT b.*
FROM equipment_bookings b
LEFT JOIN meeting m ON m.meeting_id=b.meeting_id
LEFT JOIN equipment e ON e.equipment_id=b.equipment_id
WHERE b.status <> 'CANCELLED'
  AND (b.status <> 'BOOKED' OR m.meeting_id IS NULL OR e.equipment_id IS NULL
       OR b.quantity IS NULL OR b.quantity <= 0
       OR NOT (b.room <=> m.room_id)
       OR NOT (e.room_id <=> m.room_id)
       OR NOT (b.start_time <=> m.start_time)
       OR NOT (b.end_time <=> m.end_time));

-- Review quantities where the target ERD table already contains an allocation.
SELECT b.meeting_id,b.equipment_id,SUM(b.quantity) AS legacy_quantity,me.quantity AS erd_quantity
FROM equipment_bookings b
JOIN meeting m ON m.meeting_id=b.meeting_id
JOIN meeting_equipment me ON me.meeting_id=b.meeting_id AND me.equipment_id=b.equipment_id
WHERE b.status='BOOKED' AND m.status<>'CANCELLED'
GROUP BY b.meeting_id,b.equipment_id,me.quantity
HAVING SUM(b.quantity)<>me.quantity;

-- Once BOTH checks return zero rows, inventory/timezone semantics have been
-- reviewed, and all writers are stopped, an operator can run this separately:
-- START TRANSACTION;
-- INSERT INTO meeting_equipment(meeting_id,equipment_id,quantity)
-- SELECT b.meeting_id,b.equipment_id,SUM(b.quantity)
-- FROM equipment_bookings b JOIN meeting m ON m.meeting_id=b.meeting_id
-- WHERE b.status='BOOKED' AND m.status<>'CANCELLED'
-- AND NOT EXISTS (SELECT 1 FROM meeting_equipment me
--                 WHERE me.meeting_id=b.meeting_id AND me.equipment_id=b.equipment_id)
-- GROUP BY b.meeting_id,b.equipment_id;
-- COMMIT;
-- Scheduled/partial maintenance cannot be migrated as a time-based reservation:
-- ERD has only equipment.status. Resolve those rows explicitly before cutover.
