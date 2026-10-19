
package com.ttcs.meetingmanagement.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateEquipmentRequest {

    @Size(max = 50, message = "Mã phòng không được vượt quá 50 ký tự")
    private String roomId;

    @NotBlank(message = "Tên thiết bị không được để trống")
    @Size(max = 255, message = "Tên thiết bị không được vượt quá 255 ký tự")
    private String equipmentName;

    @NotBlank(message = "Loại thiết bị không được để trống")
    @Size(max = 100, message = "Loại thiết bị không được vượt quá 100 ký tự")
    private String type;

    @NotNull(message = "Số lượng thiết bị không được để trống")
    @Min(value = 0, message = "Số lượng thiết bị phải lớn hơn hoặc bằng 0")
    private Integer totalQuantity;

    @NotBlank(message = "Trạng thái thiết bị không được để trống")
    @Pattern(
            regexp = "^\\s*(AVAILABLE|BOOKED|IN_USE|MAINTENANCE|BROKEN)\\s*$",
            message = "Trạng thái thiết bị không hợp lệ (chỉ chấp nhận: AVAILABLE, BOOKED, IN_USE, MAINTENANCE, BROKEN)"
    )
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
