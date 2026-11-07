package com.aihelpdesk.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByIdAndStatus(Long id, UserStatus status);

    List<User> findByStatusOrderByCreatedAtAsc(UserStatus status);
}
