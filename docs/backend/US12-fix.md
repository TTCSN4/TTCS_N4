# US12 equipment booking (ERD)

Based on develop 633dea6; shared commit is also an ancestor of the other three fix branches.
POST /api/meetings accepts equipment: [{"equipmentId":"EQ1","quantity":2}]. Each recurring occurrence reserves the same quantities atomically. Failure rolls back the entire meeting creation.
POST /api/equipment/bookings replaces the quantity of one equipment for a meeting. Authenticated organizer only. Supply exact meeting time and room. Cancellation: DELETE /api/equipment/bookings/{meetingId}/{equipmentId}.
Quantities are stored in meeting_equipment(meeting_id,equipment_id,quantity). Availability uses meeting times and excludes cancelled meetings. Locks serialize reservations; errors include the remaining quantity. US02 provides full edit integration.
ERD has no maintenance reservation table: use equipment.status=MAINTENANCE through the existing admin equipment API. The old scheduled-maintenance endpoint is intentionally replaced by this ERD representation.
No production data is migrated or deleted. Existing equipment_bookings data must be reviewed and migrated before deployment; do not enable this build on a populated old schema without that review.
