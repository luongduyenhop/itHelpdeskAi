package com.aihelpdesk.audit;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

public interface UserAuditLogRepository extends Repository<UserAuditLog, Long> {

    <S extends UserAuditLog> S save(S entity);

    Optional<UserAuditLog> findByEventId(String eventId);

    List<UserAuditLog> findByUserIdOrderByOccurredAtAsc(Long userId);
}
