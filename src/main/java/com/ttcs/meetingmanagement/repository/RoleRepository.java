package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, String> {
}
