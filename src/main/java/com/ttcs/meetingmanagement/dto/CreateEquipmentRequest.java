package com.ttcs.meetingmanagement.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateEquipmentRequest {

    private String roomId;

    @NotBlank(message = "Tên thiết bị không được để trống")
    private String equipmentName;

    @NotBlank(message = "Loại thiết bị không được để trống")
    private String type;

    @NotNull(message = "Số lượng thiết bị không được để trống")
    @Min(value = 0, message = "Số lượng thiết bị phải lớn hơn hoặc bằng 0")
    private Integer totalQuantity;

    @NotBlank(message = "Trạng thái thiết bị không được để trống")
    private String status;

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getEquipmentName() {
        return equipmentName;
    }

    public void setEquipmentName(String equipmentName) {
        this.equipmentName = equipmentName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Integer totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
