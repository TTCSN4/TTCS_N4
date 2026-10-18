package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, String> {
    Optional<Department> findByHrmCode(String code);
}
