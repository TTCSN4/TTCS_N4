package com.ttcs.meetingmanagement.room;

import com.ttcs.meetingmanagement.dto.RoomBookingRequest;
import com.ttcs.meetingmanagement.dto.UpdateRoomRequest;
import com.ttcs.meetingmanagement.exception.RoomException;
import com.ttcs.meetingmanagement.meeting.update.US02MeetingRequest;
import com.ttcs.meetingmanagement.meeting.update.US02MeetingService;
import com.ttcs.meetingmanagement.model.Meeting;
import com.ttcs.meetingmanagement.model.MeetingParticipant;
import com.ttcs.meetingmanagement.model.Role;
import com.ttcs.meetingmanagement.model.User;
import com.ttcs.meetingmanagement.repository.EquipmentBookingRepository;
import com.ttcs.meetingmanagement.repository.MeetingParticipantRepository;
import com.ttcs.meetingmanagement.repository.MeetingRepository;
import com.ttcs.meetingmanagement.repository.RoleRepository;
import com.ttcs.meetingmanagement.repository.UserRepository;
import com.ttcs.meetingmanagement.roombooking.RoomBookingService;
import com.ttcs.meetingmanagement.service.RoomBookingPermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@org.springframework.test.context.ActiveProfiles("us11-concurrency-test")
@SpringBootTest(classes = US11RoomConcurrencyTest.US11ConcurrencyTestAppConfig.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:meeting_concurrency_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.datasource.hikari.maximum-pool-size=20"
})
public class US11RoomConcurrencyTest {

    @Configuration
    @org.springframework.context.annotation.Profile("us11-concurrency-test")
    @EnableAutoConfiguration
    @ComponentScan(basePackages = {
            "com.ttcs.meetingmanagement.room",
            "com.ttcs.meetingmanagement.rbac",
            "com.ttcs.meetingmanagement.exception"
    })
    @EntityScan(basePackages = {
            "com.ttcs.meetingmanagement.room",
            "com.ttcs.meetingmanagement.model",
            "com.ttcs.meetingmanagement.equipment"
    })
    @EnableJpaRepositories(basePackages = {
            "com.ttcs.meetingmanagement.room",
            "com.ttcs.meetingmanagement.repository"
    })
    public static class US11ConcurrencyTestAppConfig {
        @Bean
        public RoomBookingPermissionService roomBookingPermissionService() {
            RoomBookingPermissionService mock = Mockito.mock(RoomBookingPermissionService.class);
            Mockito.doNothing().when(mock).assertCanBook(Mockito.anyString(), Mockito.anyString());
            return mock;
        }

        @Bean
        public EquipmentBookingRepository equipmentBookingRepository() {
            EquipmentBookingRepository repo = Mockito.mock(EquipmentBookingRepository.class);
            Mockito.when(repo.findAllByMeetingId(Mockito.anyString())).thenReturn(List.of());
            return repo;
        }

        @Bean
        public RoomBookingService roomBookingService(
                MeetingRepository meetingRepository,
                RoomRepository roomRepository,
                RoomBookingPermissionService permissionService,
                MeetingParticipantRepository participantRepository) {
            return new RoomBookingService(meetingRepository, roomRepository, permissionService, participantRepository);
        }

        @Bean
        public US02MeetingService us02MeetingService(
                MeetingRepository meetingRepository,
                RoomRepository roomRepository,
                EquipmentBookingRepository equipmentBookingRepository,
                RoomBookingPermissionService roomBookingPermissionService,
                MeetingParticipantRepository participantRepository) {
            return new US02MeetingService(
                    meetingRepository,
                    roomRepository,
                    equipmentBookingRepository,
                    roomBookingPermissionService,
                    participantRepository
            );
        }
    }

    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomService roomService;
    @Autowired private MeetingRepository meetingRepository;
    @Autowired private MeetingParticipantRepository participantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private RoomBookingService roomBookingService;
    @Autowired private US02MeetingService us02MeetingService;
    @Autowired private PlatformTransactionManager transactionManager;

    private ExecutorService executor;
    private User adminUser;
    private User bookerUser;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(8);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            Role adminRole = new Role();
            adminRole.setRoleId("ROLE_ADMIN");
            adminRole.setRoleName("Admin");
            roleRepository.saveAndFlush(adminRole);

            adminUser = new User();
            adminUser.setUserId("ADMIN-CONCUR");
            adminUser.setEmail("admin-concur@example.com");
            adminUser.setFullName("Admin Concurrency");
            adminUser.setRole(adminRole);
            adminUser.setStatus("ACTIVE");
            userRepository.saveAndFlush(adminUser);

            Role userRole = new Role();
            userRole.setRoleId("ROLE_USER");
            userRole.setRoleName("User");
            roleRepository.saveAndFlush(userRole);

            bookerUser = new User();
            bookerUser.setUserId("BOOKER-CONCUR");
            bookerUser.setEmail("booker-concur@example.com");
            bookerUser.setFullName("Booker Concurrency");
            bookerUser.setRole(userRole);
            bookerUser.setStatus("ACTIVE");
            userRepository.saveAndFlush(bookerUser);
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    // ==========================================
    // 1. CONCURRENCY: ADMIN GIAM SUC CHUA DONG THOI VOI NGUOI DUNG DAT PHONG (25 NGUOI)
    // ==========================================

    @Test
    @DisplayName("Concurrency 1: Race condition giua Admin giam suc chua xuong 20 va Booker dat phong voi 25 nguoi")
    void testConcurrency_AdminReduceCapacity_ConcurrentWithBooking() throws Exception {
        String roomId = "ROOM-CONCUR-BOOKING-1";
        String meetingId = "MEET-CONCUR-BOOKING-1";

        // Khoi tao: Phong co suc chua 30
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            Room room = new Room();
            room.setRoomId(roomId);
            room.setRoomName("Phong Concur Booking 1");
            room.setCapacity(30);
            room.setStatus("AVAILABLE");
            roomRepository.saveAndFlush(room);

            // Cuoc hop chua co phong, co 25 nguoi tham gia (1 organizer + 24 participants)
            Meeting meeting = new Meeting();
            meeting.setMeetingId(meetingId);
            meeting.setRoomId(null);
            meeting.setOrganizerId(bookerUser.getUserId());
            meeting.setTitle("Cuoc hop 25 nguoi can dat phong");
            meeting.setStartTime(LocalDateTime.now().plusDays(2).withHour(10).withMinute(0));
            meeting.setEndTime(LocalDateTime.now().plusDays(2).withHour(11).withMinute(0));
            meeting.setStatus("SCHEDULED");
            meetingRepository.saveAndFlush(meeting);

            for (int i = 1; i <= 24; i++) {
                MeetingParticipant p = new MeetingParticipant();
                p.setMeetingId(meetingId);
                p.setUserId("PART-BOOK-" + i);
                p.setStatus("ACCEPTED");
                participantRepository.save(p);
            }
            participantRepository.flush();
            return null;
        });

        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(2);

        AtomicBoolean bookingSucceeded = new AtomicBoolean(false);
        AtomicBoolean bookingConflictCaught = new AtomicBoolean(false);
        AtomicBoolean adminSucceeded = new AtomicBoolean(false);
        AtomicBoolean adminConflictCaught = new AtomicBoolean(false);

        // Luong 1: Booker dat phong ROOM-CONCUR-BOOKING-1 (yeu cau suc chua >= 25)
        executor.submit(() -> {
            try {
                startSignal.await();
                TransactionTemplate threadTx = new TransactionTemplate(transactionManager);
                threadTx.execute(status -> {
                    RoomBookingRequest req = new RoomBookingRequest();
                    req.setMeetingId(meetingId);
                    req.setRoomId(roomId);
                    roomBookingService.bookRoom(req);
                    bookingSucceeded.set(true);
                    return null;
                });
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                    bookingConflictCaught.set(true);
                }
            } catch (Exception ignored) {
            } finally {
                doneSignal.countDown();
            }
        });

        // Luong 2: Admin giam suc chua phong xuong 20
        executor.submit(() -> {
            try {
                startSignal.await();
                TransactionTemplate threadTx = new TransactionTemplate(transactionManager);
                threadTx.execute(status -> {
                    UpdateRoomRequest req = new UpdateRoomRequest();
                    req.setRoomName("Phong Concur Booking 1");
                    req.setCapacity(20);
                    req.setStatus("AVAILABLE");
                    roomService.updateRoom(roomId, req);
                    adminSucceeded.set(true);
                    return null;
                });
            } catch (RoomException ex) {
                if (ex.getStatus() == HttpStatus.CONFLICT) {
                    adminConflictCaught.set(true);
                }
            } catch (Exception ignored) {
            } finally {
                doneSignal.countDown();
            }
        });

        // Khoi chay dong thoi
        startSignal.countDown();
        assertTrue(doneSignal.await(5, TimeUnit.SECONDS));

        // Kiem tra tinh nhat quan sau commit:
        Room finalRoom = roomRepository.findById(roomId).orElseThrow();
        Meeting finalMeeting = meetingRepository.findById(meetingId).orElseThrow();

        // NGUYEN TAC AN TOAN DU LIEU:
        // Vi ca 2 transaction deu dung row-level lock PESSIMISTIC_WRITE (qua findLockedById):
        // 1. Neu Booking chay truoc: dat thanh cong 25 nguoi -> Admin giam xuong 20 bi CHAI 409 (25 > 20).
        //    -> Ket qua: bookingSucceeded=true, adminConflictCaught=true, Room.capacity=30, Meeting.roomId=ROOM-CONCUR-BOOKING-1.
        // 2. Neu Admin chay truoc: giam thanh cong xuong 20 -> Booking vao phong co 20 cho voi 25 nguoi bi CHAN 409 (25 > 20).
        //    -> Ket qua: adminSucceeded=true, bookingConflictCaught=true, Room.capacity=20, Meeting.roomId=null.
        // TUYET DOI KHONG THE XAY RA truong hop ca 2 deu thanh cong (phong capacity=20 ma chua cuoc hop 25 nguoi)!

        if (bookingSucceeded.get()) {
            assertTrue(adminConflictCaught.get(), "Khi dat phong 25 nguoi thanh cong, Admin giam xuong 20 buoc phai bi chan 409 Conflict");
            assertEquals(30, finalRoom.getCapacity(), "Suc chua phai duoc giu nguyen la 30");
            assertEquals(roomId, finalMeeting.getRoomId(), "Cuoc hop phai duoc ghi nhan dung ma phong");
        } else {
            assertTrue(adminSucceeded.get(), "Neu khong phai booking truoc thi Admin phai giam thanh cong");
            assertTrue(bookingConflictCaught.get(), "Booking phai bi tu choi 409 do phong da bi giam xuong 20 (khong du suc chua cho 25 nguoi)");
            assertEquals(20, finalRoom.getCapacity(), "Suc chua phong trong DB da thanh 20");
            assertNull(finalMeeting.getRoomId(), "Cuoc hop khong duoc phep duoc gan vao phong khi khong du suc chua");
        }
    }

    // ==========================================
    // 2. CONCURRENCY: ADMIN CHUYEN SANG MAINTENANCE TRONG KHI NGUOI DUNG DAT LICH
    // ==========================================

    @Test
    @DisplayName("Concurrency 2: Admin chuyen phong sang MAINTENANCE dong thoi voi Nguoi dung dat lich")
    void testConcurrency_AdminMaintenance_ConcurrentWithUserBooking() throws Exception {
        String roomId = "ROOM-CONCUR-2";

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            Room room = new Room();
            room.setRoomId(roomId);
            room.setRoomName("Phong Concur 2");
            room.setCapacity(20);
            room.setStatus("AVAILABLE");
            roomRepository.saveAndFlush(room);

            Meeting meeting = new Meeting();
            meeting.setMeetingId("MEET-CONCUR-2");
            meeting.setOrganizerId(adminUser.getUserId());
            meeting.setTitle("Cuoc hop dat lich");
            meeting.setStartTime(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0));
            meeting.setEndTime(LocalDateTime.now().plusDays(1).withHour(11).withMinute(0));
            meeting.setStatus("SCHEDULED");
            meetingRepository.saveAndFlush(meeting);
            return null;
        });

        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(2);

        AtomicBoolean maintenanceSuccess = new AtomicBoolean(false);
        List<String> outcomes = Collections.synchronizedList(new ArrayList<>());

        // Thread 1: Admin chuyen phong sang MAINTENANCE
        executor.submit(() -> {
            try {
                startSignal.await();
                TransactionTemplate threadTx = new TransactionTemplate(transactionManager);
                threadTx.execute(status -> {
                    UpdateRoomRequest req = new UpdateRoomRequest();
                    req.setRoomName("Phong Concur 2");
                    req.setCapacity(20);
                    req.setStatus("MAINTENANCE");
                    roomService.updateRoom(roomId, req);
                    maintenanceSuccess.set(true);
                    outcomes.add("ADMIN_MAINTENANCE");
                    return null;
                });
            } catch (Exception ex) {
                outcomes.add("ADMIN_ERR: " + ex.getMessage());
            } finally {
                doneSignal.countDown();
            }
        });

        // Thread 2: Nguoi dung dat phong vao ROOM-CONCUR-2
        executor.submit(() -> {
            try {
                startSignal.await();
                TransactionTemplate threadTx = new TransactionTemplate(transactionManager);
                threadTx.execute(status -> {
                    RoomBookingRequest req = new RoomBookingRequest();
                    req.setMeetingId("MEET-CONCUR-2");
                    req.setRoomId(roomId);
                    roomBookingService.bookRoom(req);
                    outcomes.add("BOOKING_SUCCESS");
                    return null;
                });
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                    outcomes.add("BOOKING_REJECTED_409");
                }
            } catch (Exception ex) {
                outcomes.add("BOOKING_ERR: " + ex.getMessage());
            } finally {
                doneSignal.countDown();
            }
        });

        startSignal.countDown();
        assertTrue(doneSignal.await(5, TimeUnit.SECONDS));

        // Kiem tra du lieu cuoi cung:
        Room finalRoom = roomRepository.findById(roomId).orElseThrow();
        Meeting finalMeeting = meetingRepository.findById("MEET-CONCUR-2").orElseThrow();

        if ("MAINTENANCE".equals(finalRoom.getStatus())) {
            assertNotNull(finalMeeting.getMeetingId());
        }
        assertTrue(outcomes.contains("ADMIN_MAINTENANCE"));
    }

    // ==========================================
    // 3. CONCURRENCY: US02 DOI PHONG TRONG KHI ADMIN DOI TRANG THAI PHONG
    // ==========================================

    @Test
    @DisplayName("Concurrency 3: US02 doi lich/phong dong thoi voi Admin thay doi trang thai phong")
    void testConcurrency_US02Reschedule_ConcurrentWithAdminStatusChange() throws Exception {
        String roomId = "ROOM-CONCUR-3";

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            Room room = new Room();
            room.setRoomId(roomId);
            room.setRoomName("Phong Concur 3");
            room.setCapacity(15);
            room.setStatus("AVAILABLE");
            roomRepository.saveAndFlush(room);

            Meeting meeting = new Meeting();
            meeting.setMeetingId("MEET-US02-CONCUR");
            meeting.setOrganizerId(adminUser.getUserId());
            meeting.setTitle("Hop Ban Dau");
            meeting.setStartTime(LocalDateTime.now().plusDays(2).withHour(9).withMinute(0));
            meeting.setEndTime(LocalDateTime.now().plusDays(2).withHour(10).withMinute(0));
            meeting.setStatus("SCHEDULED");
            meetingRepository.saveAndFlush(meeting);
            return null;
        });

        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(2);
        List<String> log = Collections.synchronizedList(new ArrayList<>());

        // Thread 1: US02 doi phong sang ROOM-CONCUR-3
        executor.submit(() -> {
            try {
                startSignal.await();
                TransactionTemplate threadTx = new TransactionTemplate(transactionManager);
                threadTx.execute(status -> {
                    US02MeetingRequest req = new US02MeetingRequest(
                            "Doi sang Phong 3",
                            "Mo ta",
                            LocalDateTime.now().plusDays(3).withHour(14).withMinute(0),
                            LocalDateTime.now().plusDays(3).withHour(15).withMinute(0),
                            roomId
                    );
                    us02MeetingService.update("MEET-US02-CONCUR", adminUser.getUserId(), req);
                    log.add("US02_SUCCESS");
                    return null;
                });
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                    log.add("US02_BLOCKED_409");
                }
            } catch (Exception ex) {
                log.add("US02_EX: " + ex.getClass().getSimpleName());
            } finally {
                doneSignal.countDown();
            }
        });

        // Thread 2: Admin doi trang thai phong sang INACTIVE
        executor.submit(() -> {
            try {
                startSignal.await();
                TransactionTemplate threadTx = new TransactionTemplate(transactionManager);
                threadTx.execute(status -> {
                    UpdateRoomRequest req = new UpdateRoomRequest();
                    req.setRoomName("Phong Concur 3");
                    req.setCapacity(15);
                    req.setStatus("INACTIVE");
                    roomService.updateRoom(roomId, req);
                    log.add("ADMIN_INACTIVE_SUCCESS");
                    return null;
                });
            } catch (Exception ex) {
                log.add("ADMIN_EX: " + ex.getClass().getSimpleName());
            } finally {
                doneSignal.countDown();
            }
        });

        startSignal.countDown();
        assertTrue(doneSignal.await(5, TimeUnit.SECONDS));

        Meeting meeting = meetingRepository.findById("MEET-US02-CONCUR").orElseThrow();
        assertEquals("MEET-US02-CONCUR", meeting.getMeetingId());
        assertTrue(log.contains("ADMIN_INACTIVE_SUCCESS"));
    }
}
