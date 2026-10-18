package com.ttcs.meetingmanagement.repository;

import com.ttcs.meetingmanagement.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    @Query("""
        select u from User u
        left join fetch u.role
        where u.userId = :id
        """)
    Optional<User> findWithRole(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select u from User u
        left join fetch u.role
        where u.userId = :id
        """)
    Optional<User> lockByIdWithRole(@Param("id") String id);

    long countByRole_RoleId(String roleId);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByHrmCode(String code);

    List<User> findByHrmCodeIsNotNull();
}
