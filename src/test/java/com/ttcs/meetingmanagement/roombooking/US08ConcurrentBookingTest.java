package com.ttcs.meetingmanagement.roombooking;

import com.ttcs.meetingmanagement.dto.RoomBookingRequest;
import com.ttcs.meetingmanagement.dto.RoomBookingResponse;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.repository.UserRepository;
import com.ttcs.meetingmanagement.room.Room;
import com.ttcs.meetingmanagement.room.RoomRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class US08ConcurrentBookingTest {

    @Autowired
    private RoomBookingService roomBookingService;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    private final String userId1 = "USER-CONCUR-1";
    private final String userId2 = "USER-CONCUR-2";
    private final String roomId = "ROOM-CONCUR-1";
    private final String roomId2 = "ROOM-CONCUR-2";

    @BeforeEach
    void setupFixtures() {
        if (!userRepository.existsById(userId1)) {
            User u1 = new User();
            u1.setUserId(userId1);
            u1.setStatus("ACTIVE");
            userRepository.saveAndFlush(u1);
        }
        if (!userRepository.existsById(userId2)) {
            User u2 = new User();
            u2.setUserId(userId2);
            u2.setStatus("ACTIVE");
            userRepository.saveAndFlush(u2);
        }

        if (!roomRepository.existsById(roomId)) {
            Room r1 = new Room();
            r1.setRoomId(roomId);
            r1.setRoomName("Concurrency Battle Room 1");
            r1.setCapacity(20);
            r1.setStatus("AVAILABLE");
            roomRepository.saveAndFlush(r1);
        }

        if (!roomRepository.existsById(roomId2)) {
            Room r2 = new Room();
            r2.setRoomId(roomId2);
            r2.setRoomName("Concurrency Battle Room 2");
            r2.setCapacity(20);
            r2.setStatus("AVAILABLE");
            roomRepository.saveAndFlush(r2);
        }
    }

    @AfterEach
    void cleanUp() {
        meetingRepository.deleteAll(meetingRepository.findAllById(List.of(
                "MEET-CC-1", "MEET-CC-2", "MEET-DIFF-1", "MEET-DIFF-2"
        )));
    }

    @Test
    @DisplayName("TC11: Hai request dong thoi cho cung phong cung khung gio -> dung mot thanh cong, mot 409 Conflict")
    void tc11_concurrentRequestsForSameRoom_exactlyOneSucceeds() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 12, 20, 14, 0);
        LocalDateTime end = LocalDateTime.of(2026, 12, 20, 15, 0);

        Meeting m1 = new Meeting();
        m1.setMeetingId("MEET-CC-1");
        m1.setOrganizerId(userId1);
        m1.setTitle("Concurrent Meeting 1");
        m1.setStartTime(start);
        m1.setEndTime(end);
        m1.setStatus("SCHEDULED");
        meetingRepository.saveAndFlush(m1);

        Meeting m2 = new Meeting();
        m2.setMeetingId("MEET-CC-2");
        m2.setOrganizerId(userId2);
        m2.setTitle("Concurrent Meeting 2");
        m2.setStartTime(start.plusMinutes(15));
        m2.setEndTime(end.plusMinutes(15)); // Khung gio giao nhau
        m2.setStatus("SCHEDULED");
        meetingRepository.saveAndFlush(m2);

        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> unexpectedExceptions = Collections.synchronizedList(new ArrayList<>());

        Callable<Void> task1 = () -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                RoomBookingRequest req = new RoomBookingRequest();
                req.setMeetingId("MEET-CC-1");
                req.setRoomId(roomId);
                roomBookingService.bookRoom(req, userId1);
                successCount.incrementAndGet();
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                    conflictCount.incrementAndGet();
                } else {
                    unexpectedExceptions.add(ex);
                }
            } catch (Throwable t) {
                unexpectedExceptions.add(t);
            }
            return null;
        };

        Callable<Void> task2 = () -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                RoomBookingRequest req = new RoomBookingRequest();
                req.setMeetingId("MEET-CC-2");
                req.setRoomId(roomId);
                roomBookingService.bookRoom(req, userId2);
                successCount.incrementAndGet();
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                    conflictCount.incrementAndGet();
                } else {
                    unexpectedExceptions.add(ex);
                }
            } catch (Throwable t) {
                unexpectedExceptions.add(t);
            }
            return null;
        };

        Future<Void> f1 = executor.submit(task1);
        Future<Void> f2 = executor.submit(task2);

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS), "Cac thread chua san sang");
        startLatch.countDown(); // Ban tin hieu dong thoi

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(unexpectedExceptions.isEmpty(), "Co loi bat ngo: " + unexpectedExceptions);

        // Kiem tra ket qua: Dung 1 thanh cong, dung 1 bi tu choi 409 Conflict
        assertEquals(1, successCount.get(), "Phai co dung 1 request thanh cong");
        assertEquals(1, conflictCount.get(), "Phai co dung 1 request bi tu choi 409 Conflict");

        // Kiem tra database: dung 1 cuoc hop giu phong
        Meeting savedM1 = meetingRepository.findById("MEET-CC-1").orElseThrow();
        Meeting savedM2 = meetingRepository.findById("MEET-CC-2").orElseThrow();

        boolean m1HasRoom = roomId.equals(savedM1.getRoomId());
        boolean m2HasRoom = roomId.equals(savedM2.getRoomId());

        assertTrue(m1HasRoom ^ m2HasRoom, "Dung 1 trong 2 cuoc hop duoc phep giu phong trong database (XOR)");
    }

    @Test
    @DisplayName("TC12: Hai request dong thoi cho hai phong khac nhau -> ca hai co the cung thanh cong")
    void tc12_concurrentRequestsForDifferentRooms_bothSucceed() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 12, 21, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 12, 21, 10, 0);

        Meeting m1 = new Meeting();
        m1.setMeetingId("MEET-DIFF-1");
        m1.setOrganizerId(userId1);
        m1.setTitle("Diff Room Meeting 1");
        m1.setStartTime(start);
        m1.setEndTime(end);
        m1.setStatus("SCHEDULED");
        meetingRepository.saveAndFlush(m1);

        Meeting m2 = new Meeting();
        m2.setMeetingId("MEET-DIFF-2");
        m2.setOrganizerId(userId2);
        m2.setTitle("Diff Room Meeting 2");
        m2.setStartTime(start);
        m2.setEndTime(end);
        m2.setStatus("SCHEDULED");
        meetingRepository.saveAndFlush(m2);

        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        List<Throwable> unexpectedExceptions = Collections.synchronizedList(new ArrayList<>());

        Callable<Void> task1 = () -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                RoomBookingRequest req = new RoomBookingRequest();
                req.setMeetingId("MEET-DIFF-1");
                req.setRoomId(roomId);
                roomBookingService.bookRoom(req, userId1);
                successCount.incrementAndGet();
            } catch (Throwable t) {
                unexpectedExceptions.add(t);
            }
            return null;
        };

        Callable<Void> task2 = () -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                RoomBookingRequest req = new RoomBookingRequest();
                req.setMeetingId("MEET-DIFF-2");
                req.setRoomId(roomId2);
                roomBookingService.bookRoom(req, userId2);
                successCount.incrementAndGet();
            } catch (Throwable t) {
                unexpectedExceptions.add(t);
            }
            return null;
        };

        Future<Void> f1 = executor.submit(task1);
        Future<Void> f2 = executor.submit(task2);

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
        startLatch.countDown();

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(unexpectedExceptions.isEmpty(), "Co loi: " + unexpectedExceptions);
        assertEquals(2, successCount.get(), "Ca 2 booking khac phong phai cung thanh cong");

        Meeting savedM1 = meetingRepository.findById("MEET-DIFF-1").orElseThrow();
        Meeting savedM2 = meetingRepository.findById("MEET-DIFF-2").orElseThrow();

        assertEquals(roomId, savedM1.getRoomId());
        assertEquals(roomId2, savedM2.getRoomId());
    }
}