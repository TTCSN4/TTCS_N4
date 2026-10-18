# US13 availability

GET /api/equipment/availability?room=R1&startTime=2099-01-01T09:00:00%2B07:00&endTime=2099-01-01T10:00:00%2B07:00

Returns availableQuantity valid for the entire interval, not the sum of all overlapping reservations. Optional excludeMeetingId supports edit previews; final booking always revalidates under a lock.
GET /api/equipment/status?room=R1&at=... remains available for point-in-time queries.
Data comes from ERD meeting_equipment joined to meeting. Cancelled meetings do not consume capacity. Changing meeting times changes availability without duplicated time columns. Non-AVAILABLE equipment cannot be reserved. US02 handles transactional replacement when editing.
This branch and US12 share the exact same foundation commit; no copied alternate entity or repository is introduced.
