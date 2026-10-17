package com.ttcs.meetingmanagement.room;

import com.ttcs.meetingmanagement.equipment.Equipment;
import com.ttcs.meetingmanagement.equipment.EquipmentRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RoomDataInitializer implements CommandLineRunner {

    private final RoomRepository roomRepository;
    private final EquipmentRepository equipmentRepository;

    public RoomDataInitializer(RoomRepository roomRepository, EquipmentRepository equipmentRepository) {
        this.roomRepository = roomRepository;
        this.equipmentRepository = equipmentRepository;
    }

    @Override
    public void run(String... args) {
        if (roomRepository.count() == 0) {
            Room r101 = new Room();
            r101.setRoomId("P.101");
            r101.setRoomName("Phòng Họp 101");
            r101.setCapacity(10);
            r101.setLocation("Tầng 1 - Tòa nhà A");
            r101.setStatus("AVAILABLE");

            Room r102 = new Room();
            r102.setRoomId("P.102");
            r102.setRoomName("Phòng Họp 102");
            r102.setCapacity(15);
            r102.setLocation("Tầng 1 - Tòa nhà A");
            r102.setStatus("AVAILABLE");

            Room r201 = new Room();
            r201.setRoomId("P.201");
            r201.setRoomName("Phòng Họp VIP");
            r201.setCapacity(25);
            r201.setLocation("Tầng 2 - Tòa nhà B");
            r201.setStatus("AVAILABLE");

            Room r202 = new Room();
            r202.setRoomId("P.202");
            r202.setRoomName("Phòng Hội Thảo Lớn");
            r202.setCapacity(50);
            r202.setLocation("Tầng 2 - Tòa nhà B");
            r202.setStatus("AVAILABLE");

            Room r301 = new Room();
            r301.setRoomId("P.301");
            r301.setRoomName("Phòng Kỹ Thuật");
            r301.setCapacity(8);
            r301.setLocation("Tầng 3 - Tòa nhà C");
            r301.setStatus("MAINTENANCE");

            Room r302 = new Room();
            r302.setRoomId("P.302");
            r302.setRoomName("Phòng Đào Tạo Cũ");
            r302.setCapacity(12);
            r302.setLocation("Tầng 3 - Tòa nhà C");
            r302.setStatus("INACTIVE");

            roomRepository.saveAll(List.of(r101, r102, r201, r202, r301, r302));
        }

        if (equipmentRepository.count() == 0) {
            Equipment eq1 = new Equipment();
            eq1.setEquipmentId("EQ-101-1");
            eq1.setRoomId("P.101");
            eq1.setEquipmentName("Máy chiếu Full HD");
            eq1.setType("PROJECTOR");
            eq1.setTotalQuantity(2);
            eq1.setStatus("AVAILABLE");

            Equipment eq2 = new Equipment();
            eq2.setEquipmentId("EQ-101-2");
            eq2.setRoomId("P.101");
            eq2.setEquipmentName("Micro không dây");
            eq2.setType("MICROPHONE");
            eq2.setTotalQuantity(4);
            eq2.setStatus("AVAILABLE");

            Equipment eq3 = new Equipment();
            eq3.setEquipmentId("EQ-102-1");
            eq3.setRoomId("P.102");
            eq3.setEquipmentName("Màn hình Tivi 65 inch");
            eq3.setType("TV");
            eq3.setTotalQuantity(1);
            eq3.setStatus("AVAILABLE");

            Equipment eq4 = new Equipment();
            eq4.setEquipmentId("EQ-201-1");
            eq4.setRoomId("P.201");
            eq4.setEquipmentName("Hệ thống Polycom Studio");
            eq4.setType("VIDEO_CONFERENCE");
            eq4.setTotalQuantity(1);
            eq4.setStatus("AVAILABLE");

            Equipment eq5 = new Equipment();
            eq5.setEquipmentId("EQ-202-1");
            eq5.setRoomId("P.202");
            eq5.setEquipmentName("Dàn âm thanh hội trường JBL");
            eq5.setType("AUDIO");
            eq5.setTotalQuantity(1);
            eq5.setStatus("AVAILABLE");

            equipmentRepository.saveAll(List.of(eq1, eq2, eq3, eq4, eq5));
        }
    }
}
