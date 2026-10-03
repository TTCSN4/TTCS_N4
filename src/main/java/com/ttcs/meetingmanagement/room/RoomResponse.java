package com.ttcs.meetingmanagement.room;

public class RoomResponse {

    private Long id;
    private String name;
    private Integer capacity;
    private RoomStatus status;

    public RoomResponse() {
    }

    public RoomResponse(
            Long id,
            String name,
            Integer capacity,
            RoomStatus status
    ) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public RoomStatus getStatus() {
        return status;
    }
}