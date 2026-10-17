package com.ttcs.meetingmanagement.dto;

public class EquipmentAvailabilityResponse {

    private String equipmentId;
    private String equipmentName;
    private String roomId;
    private Integer totalQuantity;
    private Integer bookedQuantity;
    private Integer availableQuantity;
    private String status;

    public EquipmentAvailabilityResponse() {
    }

    public EquipmentAvailabilityResponse(String equipmentId, String equipmentName, String roomId,
                                         Integer totalQuantity, Integer bookedQuantity,
                                         Integer availableQuantity, String status) {
        this.equipmentId = equipmentId;
        this.equipmentName = equipmentName;
        this.roomId = roomId;
        this.totalQuantity = totalQuantity;
        this.bookedQuantity = bookedQuantity;
        this.availableQuantity = availableQuantity;
        this.status = status;
    }

    public String getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(String equipmentId) {
        this.equipmentId = equipmentId;
    }

    public String getEquipmentName() {
        return equipmentName;
    }

    public void setEquipmentName(String equipmentName) {
        this.equipmentName = equipmentName;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public Integer getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Integer totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public Integer getBookedQuantity() {
        return bookedQuantity;
    }

    public void setBookedQuantity(Integer bookedQuantity) {
        this.bookedQuantity = bookedQuantity;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(Integer availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
