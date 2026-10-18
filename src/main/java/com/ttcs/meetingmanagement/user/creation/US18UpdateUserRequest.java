package com.ttcs.meetingmanagement.user.creation;
import jakarta.validation.constraints.*;
public record US18UpdateUserRequest(@NotBlank @Size(max=255) String fullName,@Email @NotBlank String email,
    @Pattern(regexp="^$|^[0-9+() .-]{7,20}$") String phone,@NotBlank String roleId,String departmentId) {}
