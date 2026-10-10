
package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface UserRepository
        extends JpaRepository<User, String> {

    @Query("""
        select u from User u
        left join fetch u.role
        where u.userId = :id
        """)
    Optional<User> findWithRole(
            @Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select u from User u
        left join fetch u.role
        where u.userId = :id
        """)
    Optional<User> lockByIdWithRole(
            @Param("id") String id);

    long countByRole_RoleId(String roleId);
}
