package com.aihelpdesk.audit;

import com.aihelpdesk.user.Role;
import com.aihelpdesk.user.User;
import com.aihelpdesk.user.UserRepository;
import com.aihelpdesk.user.UserStatus;
import com.aihelpdesk.user.event.UserLifecycleEvent;
import com.aihelpdesk.user.event.UserLifecycleEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.Ordered;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class UserLifecycleAuditIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuditLogRepository auditLogRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM user_audit_logs");
        userRepository.deleteAll();
    }

    @Test
    void shouldPersistUserAndAuditAtomicallyOnCommit() {
        String eventId = UUID.randomUUID().toString();
        String email = "employee.audit@utc.edu.vn";

        transactionTemplate.executeWithoutResult(status -> {
            User user = new User(email, "hashedPassword", "Audit Test Employee", Role.EMPLOYEE, UserStatus.PENDING);
            userRepository.save(user);

            UserLifecycleEvent event = new UserLifecycleEvent(
                eventId,
                user.getId(),
                null,
                ActorType.SYSTEM,
                UserLifecycleEventType.USER_REGISTERED,
                null,
                UserStatus.PENDING,
                LocalDateTime.now(),
                "Public registration"
            );
            eventPublisher.publishEvent(event);
        });

        Optional<User> savedUser = userRepository.findByEmail(email);
        assertThat(savedUser).isPresent();

        Optional<UserAuditLog> savedAudit = auditLogRepository.findByEventId(eventId);
        assertThat(savedAudit).isPresent();

        UserAuditLog auditLog = savedAudit.get();
        assertThat(auditLog.getUserId()).isEqualTo(savedUser.get().getId());
        assertThat(auditLog.getActorId()).isNull();
        assertThat(auditLog.getActorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(auditLog.getEventType()).isEqualTo(UserLifecycleEventType.USER_REGISTERED);
        assertThat(auditLog.getFromStatus()).isNull();
        assertThat(auditLog.getToStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(auditLog.getReason()).isEqualTo("Public registration");
        assertThat(auditLog.getOccurredAt()).isNotNull();
        assertThat(auditLog.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldRollbackBothUserAndAuditWhenExceptionOccursBeforeCommitCompletion() {
        String eventId = UUID.randomUUID().toString();
        String email = "rollback.audit@utc.edu.vn";

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            User user = new User(email, "hashedPassword", "Rollback User", Role.EMPLOYEE, UserStatus.PENDING);
            userRepository.save(user);

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public int getOrder() {
                    return Ordered.LOWEST_PRECEDENCE;
                }

                @Override
                public void beforeCommit(boolean readOnly) {
                    Integer inTxAuditCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM user_audit_logs WHERE event_id = ?",
                        Integer.class,
                        eventId
                    );
                    assertThat(inTxAuditCount).isEqualTo(1);

                    throw new RuntimeException("Forced failure after audit insert");
                }
            });

            UserLifecycleEvent event = new UserLifecycleEvent(
                eventId,
                user.getId(),
                null,
                ActorType.SYSTEM,
                UserLifecycleEventType.USER_REGISTERED,
                null,
                UserStatus.PENDING,
                LocalDateTime.now(),
                "Registration before rollback"
            );
            eventPublisher.publishEvent(event);
        })).isInstanceOf(RuntimeException.class).hasMessageContaining("Forced failure after audit insert");

        assertThat(userRepository.findByEmail(email)).isEmpty();
        assertThat(auditLogRepository.findByEventId(eventId)).isEmpty();
    }

    @Test
    void shouldNotExecuteListenerWhenEventPublishedOutsideTransaction() {
        String eventId = UUID.randomUUID().toString();
        UserLifecycleEvent event = new UserLifecycleEvent(
            eventId,
            99999L,
            null,
            ActorType.SYSTEM,
            UserLifecycleEventType.USER_REGISTERED,
            null,
            UserStatus.PENDING,
            LocalDateTime.now(),
            "Non-transactional event"
        );

        eventPublisher.publishEvent(event);

        assertThat(auditLogRepository.findByEventId(eventId)).isEmpty();
    }

    @Test
    void shouldEnforceEventIdUniqueness() {
        User user = new User("unique.audit@utc.edu.vn", "hash", "Unique User", Role.EMPLOYEE, UserStatus.PENDING);
        userRepository.save(user);

        String eventId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        UserAuditLog firstLog = new UserAuditLog(
            eventId,
            user.getId(),
            null,
            ActorType.SYSTEM,
            UserLifecycleEventType.USER_REGISTERED,
            null,
            UserStatus.PENDING,
            "First record",
            now
        );
        auditLogRepository.save(firstLog);

        UserAuditLog duplicateLog = new UserAuditLog(
            eventId,
            user.getId(),
            null,
            ActorType.SYSTEM,
            UserLifecycleEventType.USER_REGISTERED,
            null,
            UserStatus.PENDING,
            "Duplicate record",
            now
        );

        assertThatThrownBy(() -> auditLogRepository.save(duplicateLog))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldEnforceActorConsistencyCheckConstraint() {
        User user = new User("actor.audit@utc.edu.vn", "hash", "Actor Consistency User", Role.EMPLOYEE, UserStatus.PENDING);
        userRepository.save(user);

        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO user_audit_logs (event_id, user_id, actor_id, actor_type, event_type, from_status, to_status, occurred_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID().toString(),
            user.getId(),
            user.getId(),
            "SYSTEM",
            "USER_REGISTERED",
            null,
            "PENDING",
            LocalDateTime.now()
        )).hasMessageContaining("chk_user_audit_logs_actor_consistency");

        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO user_audit_logs (event_id, user_id, actor_id, actor_type, event_type, from_status, to_status, occurred_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID().toString(),
            user.getId(),
            null,
            "USER",
            "USER_REGISTERED",
            null,
            "PENDING",
            LocalDateTime.now()
        )).hasMessageContaining("chk_user_audit_logs_actor_consistency");
    }

    @Test
    void shouldEnforceEventTypeAndStatusCheckConstraints() {
        User user = new User("enums.audit@utc.edu.vn", "hash", "Enum Check User", Role.EMPLOYEE, UserStatus.PENDING);
        userRepository.save(user);

        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO user_audit_logs (event_id, user_id, actor_id, actor_type, event_type, from_status, to_status, occurred_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID().toString(),
            user.getId(),
            null,
            "SYSTEM",
            "INVALID_EVENT_TYPE",
            null,
            "PENDING",
            LocalDateTime.now()
        )).hasMessageContaining("chk_user_audit_logs_event_type");

        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO user_audit_logs (event_id, user_id, actor_id, actor_type, event_type, from_status, to_status, occurred_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID().toString(),
            user.getId(),
            null,
            "SYSTEM",
            "USER_REGISTERED",
            null,
            "INVALID_STATUS",
            LocalDateTime.now()
        )).hasMessageContaining("chk_user_audit_logs_to_status");
    }

    @Test
    void shouldEnforceForeignKeyConstraintOnUserId() {
        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO user_audit_logs (event_id, user_id, actor_id, actor_type, event_type, from_status, to_status, occurred_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID().toString(),
            999999L,
            null,
            "SYSTEM",
            "USER_REGISTERED",
            null,
            "PENDING",
            LocalDateTime.now()
        )).hasMessageContaining("fk_user_audit_logs_user");
    }
}
