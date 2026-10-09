
package com.ttcs.meetingmanagement.recurrence;

import com.ttcs.meetingmanagement.dto.RecurringMeetingRequest;

public final class RecurringMeetingRules {

    private RecurringMeetingRules() {
    }

    public static void validate(RecurringMeetingRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Request khong duoc de trong");
        }

        if (request.organizerId() == null
                || request.organizerId().isBlank()) {
            throw new IllegalArgumentException(
                    "Organizer ID khong hop le");
        }

        if (request.roomId() == null
                || request.roomId().isBlank()) {
            throw new IllegalArgumentException(
                    "Room ID khong hop le");
        }

        if (request.title() == null
                || request.title().isBlank()) {
            throw new IllegalArgumentException(
                    "Tieu de cuoc hop khong duoc de trong");
        }

        if (request.startTime() == null
                || request.endTime() == null) {
            throw new IllegalArgumentException(
                    "Thoi gian hop khong duoc de trong");
        }

        if (!request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException(
                    "Thoi gian ket thuc phai sau bat dau");
        }

        if (request.frequency() == null) {
            throw new IllegalArgumentException(
                    "Tan suat lap khong hop le");
        }

        if (request.interval() == null
                || request.interval() < 1) {
            throw new IllegalArgumentException(
                    "Khoang lap phai lon hon 0");
        }

        if (request.occurrences() == null
                || request.occurrences() < 1) {
            throw new IllegalArgumentException(
                    "So lan hop phai lon hon 0");
        }
    }

    public static String buildRule(
            RecurringMeetingRequest request) {

        validate(request);

        return "FREQ=" + request.frequency()
                + ";INTERVAL=" + request.interval()
                + ";COUNT=" + request.occurrences();
    }
}
