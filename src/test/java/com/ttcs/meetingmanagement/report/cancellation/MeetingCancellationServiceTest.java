
package com.ttcs.meetingmanagement.report.cancellation;

import com.ttcs.meetingmanagement.report.cancellation
        .MeetingCancellationRepository.MeetingRow;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeetingCancellationServiceTest {

    private final MeetingCancellationRepository repository =
            mock(MeetingCancellationRepository.class);

    private final MeetingCancellationService service =
            new MeetingCancellationService(repository);

    private final LocalDate from =
            LocalDate.of(2026, 10, 1);

    private final LocalDate to =
            LocalDate.of(2026, 10, 3);

    // Tao du lieu gia lap - KHONG su dung MySQL
    private List<MeetingRow> sampleMeetings() {

        return List.of(
                createMeeting(
                        "M001", "R001", 1, 9, "COMPLETED"),
                createMeeting(
                        "M002", "R001", 1, 10, "COMPLETED"),
                createMeeting(
                        "M003", "R001", 1, 16, "CANCELLED"),
                createMeeting(
                        "M004", "R001", 2, 23, "COMPLETED"),
                createMeeting(
                        "M005", "R002", 1, 13, "COMPLETED")
        );
    }

    private MeetingRow createMeeting(
            String meetingId,
            String roomId,
            int day,
            int hour,
            String status
    ) {
        return new MeetingRow(
                meetingId,
                roomId,
                roomId.equals("R001")
                        ? "Phong hop A"
                        : "Phong hop B",
                "U001",
                "Nguoi quan tri",
                LocalDateTime.of(
                        2026, 10, day, hour, 0
                ),
                status
        );
    }

    // TEST 1: Tong hop so luong va ty le huy
    @Test
    void shouldCalculateOverallCancellationRate() {

        when(repository.findMeetings(
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay(),
                null,
                null
        )).thenReturn(sampleMeetings());

        MeetingCancellationResponse result =
                service.generateReport(
                        from, to, null, null
                );

        assertEquals(5, result.totalMeetings());
        assertEquals(1, result.cancelledMeetings());
        assertEquals(
                20.0,
                result.cancellationRate(),
                0.001
        );

        assertEquals(3, result.byDate().size());
        assertEquals(2, result.byRoom().size());
        assertEquals(1, result.byOrganizer().size());

        // Ngay 01/10 co 4 cuoc hop, 1 bi huy
        assertEquals(
                4,
                result.byDate().get(0).totalMeetings()
        );

        assertEquals(
                1,
                result.byDate().get(0).cancelledMeetings()
        );

        // Ngay 03/10 khong co cuoc hop
        assertEquals(
                0,
                result.byDate().get(2).totalMeetings()
        );
    }

    // TEST 2: Loc theo phong R001
    @Test
    void shouldFilterByRoom() {

        when(repository.roomExists("R001"))
                .thenReturn(true);

        List<MeetingRow> roomMeetings =
                sampleMeetings().stream()
                        .filter(
                                m -> "R001".equals(m.roomId())
                        )
                        .toList();

        when(repository.findMeetings(
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay(),
                "R001",
                null
        )).thenReturn(roomMeetings);

        MeetingCancellationResponse result =
                service.generateReport(
                        from, to, "R001", null
                );

        assertEquals(4, result.totalMeetings());
        assertEquals(1, result.cancelledMeetings());
        assertEquals(
                25.0,
                result.cancellationRate(),
                0.001
        );
        assertEquals(1, result.byRoom().size());
    }

    // TEST 3: Loc theo nguoi to chuc
    @Test
    void shouldFilterByOrganizer() {

        when(repository.organizerExists("U001"))
                .thenReturn(true);

        when(repository.findMeetings(
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay(),
                null,
                "U001"
        )).thenReturn(sampleMeetings());

        MeetingCancellationResponse result =
                service.generateReport(
                        from, to, null, "U001"
                );

        assertEquals(5, result.totalMeetings());
        assertEquals(1, result.cancelledMeetings());
        assertEquals(
                20.0,
                result.cancellationRate(),
                0.001
        );
        assertEquals(1, result.byOrganizer().size());
    }

    // TEST 4: Khong co cuoc hop
    @Test
    void shouldReturnZeroForEmptyReport() {

        when(repository.findMeetings(
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay(),
                null,
                null
        )).thenReturn(List.of());

        MeetingCancellationResponse result =
                service.generateReport(
                        from, to, null, null
                );

        assertEquals(0, result.totalMeetings());
        assertEquals(0, result.cancelledMeetings());
        assertEquals(
                0.0,
                result.cancellationRate(),
                0.001
        );
    }

    // TEST 5: Ngay ket thuc nho hon ngay bat dau
    @Test
    void shouldRejectInvalidDateRange() {

        ResponseStatusException error =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.generateReport(
                                to, from, null, null
                        )
                );

        assertEquals(
                400,
                error.getStatusCode().value()
        );

        verifyNoInteractions(repository);
    }

    // TEST 6: Phong khong ton tai
    @Test
    void shouldRejectNonexistentRoom() {

        when(repository.roomExists("R999"))
                .thenReturn(false);

        ResponseStatusException error =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.generateReport(
                                from, to, "R999", null
                        )
                );

        assertEquals(
                404,
                error.getStatusCode().value()
        );
    }
}
