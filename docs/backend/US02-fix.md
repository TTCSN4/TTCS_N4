# US02 full edit

PUT /api/meetings/{id}: title, description, startTime, endTime, roomId, participantIds, equipment [{equipmentId,quantity}], recurrenceRule, scope (INSTANCE default or SERIES).
Null optional lists preserve current allocation; empty lists clear it. Blank roomId removes the room, requiring empty equipment. Errors roll back all updates. Existing participant response status is preserved when the participant remains.
INSTANCE edits detach that occurrence from the recurring series. SERIES edits all remaining future SCHEDULED occurrences; historical and cancelled occurrences are retained. To change recurrence use FREQ=WEEKLY|MONTHLY;INTERVAL=n;COUNT=n (COUNT is the number of remaining occurrences, maximum 104). The selected occurrence anchors its position. NONE keeps the selected occurrence and cancels other future occurrences, never deleting history. Arbitrary RRULE components are rejected rather than silently ignored.
The authenticated organizer or active Admin may edit. Room permission, capacity, participant conflicts and equipment stock are rechecked. Equipment is replaced atomically using ERD meeting_equipment. No duplicate PUT/DELETE controller routes remain.
