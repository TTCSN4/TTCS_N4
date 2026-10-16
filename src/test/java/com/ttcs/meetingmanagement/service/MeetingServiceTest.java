package com.ttcs.meetingmanagement.service;

import com.ttcs.meetingmanagement.dto.CreateMeetingRequest;
import com.ttcs.meetingmanagement.dto.MeetingResponse;
import com.ttcs.meetingmanagement.exception.MeetingException;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.MeetingStatus;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class MeetingServiceTest {

    @Autowired
    private MeetingService meetingService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    private final String testRoomId = "ROOM-US01-TEST";

    @BeforeEach
    void setUp() {
        if (!roomRepository.existsById(testRoomId)) {
            Room room = new Room();
            room.setRoomId(testRoomId);
            room.setRoomName("Phòng Họp Thử Nghiệm US01");
            room.setCapacity(20);
            room.setStatus("AVAILABLE");
            roomRepository.save(room);
        }
    }

    @Test
    void tc01_createMeeting_validData_success() {
        CreateMeetingRequest request = new CreateMeetingRequest();
        request.setTitle("Họp Ban Giám Đốc");
        request.setDescription("Thảo luận kế hoạch quý 4");
        request.setOrganizerId("1");
        request.setRoomId(testRoomId);
        request.setStartTime(LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0));
        request.setEndTime(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0));
        request.setIsRecurring(false);

        MeetingResponse response = meetingService.createMeeting(request);

        assertNotNull(response);
        assertNotNull(response.getMeetingId());
        assertEquals("Họp Ban Giám Đốc", response.getTitle());
        assertEquals("Thảo luận kế hoạch quý 4", response.getDescription());
        assertEquals("1", response.getOrganizerId());
        assertEquals(testRoomId, response.getRoomId());
        assertEquals("SCHEDULED", response.getStatus());
        assertFalse(response.getIsRecurring());
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getUpdatedAt());
    }

    @Test
    void tc02_createMeeting_missingOrganizer_throwsException() {
        CreateMeetingRequest request = new CreateMeetingRequest();
        request.setTitle("Họp Không Organizer");
        request.setOrganizerId(null);
        request.setStartTime(LocalDateTime.now().plusDays(1).withHour(9).withMinute(0));
        request.setEndTime(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0));

        MeetingException ex = assertThrows(MeetingException.class, () -> meetingService.createMeeting(request));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void tc03_createMeeting_blankTitle_throwsBadRequest() {
        CreateMeetingRequest request = new CreateMeetingRequest();
        request.setTitle("   ");
        request.setOrganizerId("1");
        request.setStartTime(LocalDateTime.now().plusDays(1).withHour(9).withMinute(0));
        request.setEndTime(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0));

        MeetingException ex = assertThrows(MeetingException.class, () -> meetingService.createMeeting(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Tiêu đề cuộc họp không được để trống", ex.getMessage());
    }

    @Test
    void tc04_createMeeting_endTimeBeforeStartTime_throwsBadRequest() {
        CreateMeetingRequest request = new CreateMeetingRequest();
        request.setTitle("Họp Sai Thời Gian");
        request.setOrganizerId("1");
        request.setStartTime(LocalDateTime.now().plusDays(1).withHour(11).withMinute(0));
        request.setEndTime(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0));

        MeetingException ex = assertThrows(MeetingException.class, () -> meetingService.createMeeting(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Thời gian kết thúc phải sau thời gian bắt đầu", ex.getMessage());
    }

    @Test
    void tc06_createMeeting_nonExistentRoom_throwsNotFound() {
        CreateMeetingRequest request = new CreateMeetingRequest();
        request.setTitle("Họp Phòng Ảo");
        request.setOrganizerId("1");
        request.setRoomId("ROOM-NON-EXISTENT-999");
        request.setStartTime(LocalDateTime.now().plusDays(1).withHour(9).withMinute(0));
        request.setEndTime(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0));

        MeetingException ex = assertThrows(MeetingException.class, () -> meetingService.createMeeting(request));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("Phòng họp không tồn tại", ex.getMessage());
    }

    @Test
    void tc07_createMeeting_roomConflict_throwsConflict() {
        LocalDateTime start = LocalDateTime.now().plusDays(2).withHour(14).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = LocalDateTime.now().plusDays(2).withHour(15).withMinute(0).withSecond(0).withNano(0);

        CreateMeetingRequest firstRequest = new CreateMeetingRequest();
        firstRequest.setTitle("Cuộc Họp Đã Đặt Trước");
        firstRequest.setOrganizerId("1");
        firstRequest.setRoomId(testRoomId);
        firstRequest.setStartTime(start);
        firstRequest.setEndTime(end);

        meetingService.createMeeting(firstRequest);

        CreateMeetingRequest conflictRequest = new CreateMeetingRequest();
        conflictRequest.setTitle("Cuộc Họp Trùng Lịch");
        conflictRequest.setOrganizerId("1");
        conflictRequest.setRoomId(testRoomId);
        conflictRequest.setStartTime(start.plusMinutes(15));
        conflictRequest.setEndTime(end.plusMinutes(15));

        MeetingException ex = assertThrows(MeetingException.class, () -> meetingService.createMeeting(conflictRequest));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Phòng họp đã được đặt trong khoảng thời gian này", ex.getMessage());
    }

    @Test
    void tc08_createMeeting_recurring_success() {
        CreateMeetingRequest request = new CreateMeetingRequest();
        request.setTitle("Họp Định Kỳ Hàng Tuần");
        request.setOrganizerId("1");
        request.setRoomId(testRoomId);
        request.setStartTime(LocalDateTime.now().plusDays(3).withHour(9).withMinute(0));
        request.setEndTime(LocalDateTime.now().plusDays(3).withHour(10).withMinute(0));
        request.setIsRecurring(true);
        request.setRecurrenceRule("FREQ=WEEKLY;INTERVAL=1");

        MeetingResponse response = meetingService.createMeeting(request);

        assertNotNull(response);
        assertTrue(response.getIsRecurring());
        assertEquals("FREQ=WEEKLY;INTERVAL=1", response.getRecurrenceRule());
        assertNotNull(response.getRecurrenceSeriesId());
    }
}
