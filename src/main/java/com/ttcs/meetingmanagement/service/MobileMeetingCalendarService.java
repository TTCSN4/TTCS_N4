
package com.ttcs.meetingmanagement.service;

import com.ttcs.meetingmanagement.dto.MobileCalendarPageResponse;
import com.ttcs.meetingmanagement.dto.MobileMeetingDetailResponse;
import com.ttcs.meetingmanagement.dto.MobileMeetingSummaryResponse;

import com.ttcs.meetingmanagement.exception.ResourceNotFoundException;
import com.ttcs.meetingmanagement.repository.MobileMeetingCalendarRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class MobileMeetingCalendarService {

    private final MobileMeetingCalendarRepository repository;

    public MobileMeetingCalendarService(
            MobileMeetingCalendarRepository repository
    ) {
        this.repository = repository;
    }

    // API DANH SACH LICH HOP
    @Transactional(readOnly = true)
    public MobileCalendarPageResponse getCalendar(
            String userId,
            LocalDate from,
            LocalDate to,
            int page,
            int size
    ) {

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException(
                    "Nguoi dung khong hop le"
            );
        }

        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException(
                    "Khoang ngay khong hop le"
            );
        }

        if (ChronoUnit.DAYS.between(from, to) > 366) {
            throw new IllegalArgumentException(
                    "Khoang ngay toi da 366 ngay"
            );
        }

        if (page < 0 || size < 1 || size > 50) {
            throw new IllegalArgumentException(
                    "page >= 0 va size tu 1 den 50"
            );
        }

        var fromInclusive = from.atStartOfDay();
        var toExclusive = to.plusDays(1).atStartOfDay();

        long total = repository.countVisibleMeetings(
                userId,
                fromInclusive,
                toExclusive
        );

        long offset = (long) page * size;

        List<MobileMeetingSummaryResponse> content =
                offset >= total
                ? List.of()
                : repository.findVisibleMeetings(
                        userId,
                        fromInclusive,
                        toExclusive,
                        size,
                        offset
                );

        int totalPages = (int) Math.ceil(
                (double) total / size
        );

        return new MobileCalendarPageResponse(
                content,
                page,
                size,
                total,
                totalPages
        );
    }

    // API CHI TIET CUOC HOP
    @Transactional(readOnly = true)
    public MobileMeetingDetailResponse getDetail(
            String userId,
            String meetingId
    ) {

        if (userId == null || userId.isBlank()
                || meetingId == null || meetingId.isBlank()) {

            throw new IllegalArgumentException(
                    "User ID hoac Meeting ID khong hop le"
            );
        }

        return repository.findVisibleById(
                userId,
                meetingId
        ).orElseThrow(() ->
                new ResourceNotFoundException(
                        "Cuoc hop khong ton tai hoac khong co quyen xem"
                )
        );
    }
}
