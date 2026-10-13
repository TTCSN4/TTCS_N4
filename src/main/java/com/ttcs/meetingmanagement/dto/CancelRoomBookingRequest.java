package com.ttcs.meetingmanagement.dto;

public class CancelRoomBookingRequest {

    private String cancelReason;

    public CancelRoomBookingRequest() {
    }

    public CancelRoomBookingRequest(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }
}
