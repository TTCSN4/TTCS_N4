package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, String> {
}
