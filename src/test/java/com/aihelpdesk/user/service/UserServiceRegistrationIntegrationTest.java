package com.aihelpdesk.user.service;

import com.aihelpdesk.audit.ActorType;
import com.aihelpdesk.common.exception.InvalidEmailDomainException;
import com.aihelpdesk.user.AllowedEmailDomain;
import com.aihelpdesk.user.AllowedEmailDomainRepository;
import com.aihelpdesk.user.Role;
import com.aihelpdesk.user.User;
import com.aihelpdesk.user.UserRepository;
import com.aihelpdesk.user.UserStatus;
import com.aihelpdesk.user.dto.RegisterEmployeeRequest;
import com.aihelpdesk.user.dto.RegisterEmployeeResponse;
import com.aihelpdesk.user.event.UserLifecycleEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@Import(UserServiceRegistrationIntegrationTest.TestAuditFailureConfig.class)
class UserServiceRegistrationIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AllowedEmailDomainRepository allowedEmailDomainRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private Clock clock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private User defaultAdmin;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM user_audit_logs");
        jdbcTemplate.update("DELETE FROM allowed_email_domains");
        jdbcTemplate.update("UPDATE users SET approved_by = NULL, approved_at = NULL");
        jdbcTemplate.update("DELETE FROM users");

        defaultAdmin = new User("admin.system@utc.edu.vn", "hashAdmin123", "System Admin", Role.ADMIN, UserStatus.ACTIVE);
        userRepository.save(defaultAdmin);

        AllowedEmailDomain activeDomain = new AllowedEmailDomain("utc.edu.vn", true, defaultAdmin);
        allowedEmailDomainRepository.save(activeDomain);

        AllowedEmailDomain inactiveDomain = new AllowedEmailDomain("inactive.com", false, defaultAdmin);
        allowedEmailDomainRepository.save(inactiveDomain);
    }

    @Test
    void shouldCreatePendingEmployeeOnSuccessfulRegistration() {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "employee.success@utc.edu.vn",
            "Password123!",
            "Nguyen Van A"
        );

        RegisterEmployeeResponse response = userService.registerEmployee(request);

        assertThat(response).isEqualTo(RegisterEmployeeResponse.defaultResponse());

        User user = userRepository.findByEmail("employee.success@utc.edu.vn").orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(user.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(user.getApprovedBy()).isNull();
        assertThat(user.getApprovedAt()).isNull();
        assertThat(user.getDisabledAt()).isNull();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldNormalizeEmailBeforeSaving() {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "  Employee.Normalize@UTC.EDU.VN  ",
            "Password123!",
            "Nguyen Van B"
        );

        userService.registerEmployee(request);

        assertThat(userRepository.findByEmail("employee.normalize@utc.edu.vn")).isPresent();
        assertThat(userRepository.findByEmail("  Employee.Normalize@UTC.EDU.VN  ")).isEmpty();
    }

    @Test
    void shouldHashPasswordWithBcrypt() {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "employee.hash@utc.edu.vn",
            "MySecretP@ssword123",
            "Nguyen Van C"
        );

        userService.registerEmployee(request);

        User user = userRepository.findByEmail("employee.hash@utc.edu.vn").orElseThrow();
        assertThat(user.getPasswordHash()).startsWith("$2a$12$");
        assertThat(passwordEncoder.matches("MySecretP@ssword123", user.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("WrongPassword", user.getPasswordHash())).isFalse();
    }

    @Test
    void shouldRejectInvalidDomain() {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "employee@unknown-domain.com",
            "Password123!",
            "Nguyen Van D"
        );

        assertThatThrownBy(() -> userService.registerEmployee(request))
            .isInstanceOf(InvalidEmailDomainException.class);

        assertThat(userRepository.findByEmail("employee@unknown-domain.com")).isEmpty();
    }

    @Test
    void shouldRejectInactiveDomain() {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "employee@inactive.com",
            "Password123!",
            "Nguyen Van E"
        );

        assertThatThrownBy(() -> userService.registerEmployee(request))
            .isInstanceOf(InvalidEmailDomainException.class);

        assertThat(userRepository.findByEmail("employee@inactive.com")).isEmpty();
    }

    @Test
    void shouldRejectUnregisteredSubdomain() {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "employee@sub.utc.edu.vn",
            "Password123!",
            "Nguyen Van F"
        );

        assertThatThrownBy(() -> userService.registerEmployee(request))
            .isInstanceOf(InvalidEmailDomainException.class);

        assertThat(userRepository.findByEmail("employee@sub.utc.edu.vn")).isEmpty();
    }

    @Test
    void shouldReturnNeutralResponseForDuplicateNormalizedEmail() {
        RegisterEmployeeRequest firstRequest = new RegisterEmployeeRequest(
            "duplicate@utc.edu.vn",
            "Password123!",
            "Original Name"
        );
        userService.registerEmployee(firstRequest);

        RegisterEmployeeRequest duplicateRequest = new RegisterEmployeeRequest(
            "  DUPLICATE@utc.edu.vn  ",
            "DifferentPassword456!",
            "Second Name"
        );
        RegisterEmployeeResponse response = userService.registerEmployee(duplicateRequest);

        assertThat(response).isEqualTo(RegisterEmployeeResponse.defaultResponse());
    }

    @Test
    void shouldNotChangeExistingPasswordOnDuplicateRegistration() {
        RegisterEmployeeRequest firstRequest = new RegisterEmployeeRequest(
            "existing.password@utc.edu.vn",
            "OriginalPassword123!",
            "Original Name"
        );
        userService.registerEmployee(firstRequest);

        User originalUser = userRepository.findByEmail("existing.password@utc.edu.vn").orElseThrow();
        String originalHash = originalUser.getPasswordHash();

        RegisterEmployeeRequest duplicateRequest = new RegisterEmployeeRequest(
            "existing.password@utc.edu.vn",
            "ChangedPassword456!",
            "Different Name"
        );
        userService.registerEmployee(duplicateRequest);

        User userAfter = userRepository.findByEmail("existing.password@utc.edu.vn").orElseThrow();
        assertThat(userAfter.getPasswordHash()).isEqualTo(originalHash);
        assertThat(passwordEncoder.matches("OriginalPassword123!", userAfter.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("ChangedPassword456!", userAfter.getPasswordHash())).isFalse();
    }

    @Test
    void shouldNotCreateAuditRecordOnDuplicateRegistration() {
        RegisterEmployeeRequest firstRequest = new RegisterEmployeeRequest(
            "audit.check@utc.edu.vn",
            "Password123!",
            "First Registration"
        );
        userService.registerEmployee(firstRequest);

        Integer auditCountAfterFirst = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_audit_logs",
            Integer.class
        );
        assertThat(auditCountAfterFirst).isEqualTo(1);

        RegisterEmployeeRequest duplicateRequest = new RegisterEmployeeRequest(
            "audit.check@utc.edu.vn",
            "Password123!",
            "Second Registration"
        );
        userService.registerEmployee(duplicateRequest);

        Integer auditCountAfterSecond = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_audit_logs",
            Integer.class
        );
        assertThat(auditCountAfterSecond).isEqualTo(1);
    }

    @Test
    void shouldPublishExactlyOneUserRegisteredEvent() {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "single.event@utc.edu.vn",
            "Password123!",
            "Audit Single Event"
        );
        userService.registerEmployee(request);

        User user = userRepository.findByEmail("single.event@utc.edu.vn").orElseThrow();

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_audit_logs WHERE user_id = ? AND event_type = 'USER_REGISTERED'",
            Integer.class,
            user.getId()
        );
        assertThat(count).isEqualTo(1);

        Map<String, Object> audit = jdbcTemplate.queryForMap(
            "SELECT * FROM user_audit_logs WHERE user_id = ?",
            user.getId()
        );
        assertThat(audit.get("actor_type")).isEqualTo("SYSTEM");
        assertThat(audit.get("actor_id")).isNull();
        assertThat(audit.get("from_status")).isNull();
        assertThat(audit.get("to_status")).isEqualTo("PENDING");
    }

    @Test
    void shouldRollbackUserCreationWhenAuditInsertionFails() {
        TestAuditFailureConfig.shouldFail = true;
        try {
            RegisterEmployeeRequest request = new RegisterEmployeeRequest(
                "audit.fail@utc.edu.vn",
                "Password123!",
                "Audit Fail User"
            );

            assertThatThrownBy(() -> userService.registerEmployee(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Injected audit listener failure");

            assertThat(userRepository.findByEmail("audit.fail@utc.edu.vn")).isEmpty();
            Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_audit_logs WHERE event_id = 'audit.fail@utc.edu.vn'",
                Integer.class
            );
            assertThat(auditCount).isZero();
        } finally {
            TestAuditFailureConfig.shouldFail = false;
        }
    }

    @Test
    void shouldNotTranslateNonEmailIntegrityFailureIntoDuplicateRegistration() {
        UserRepository mockUserRepo = mock(UserRepository.class);
        when(mockUserRepo.existsByEmail(any())).thenReturn(false);
        when(mockUserRepo.save(any())).thenThrow(
            new DataIntegrityViolationException("foreign key constraint fail", new SQLException("foreign key fail"))
        );

        UserService localService = new UserServiceImpl(
            mockUserRepo,
            allowedEmailDomainRepository,
            passwordEncoder,
            eventPublisher,
            clock
        );

        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "employee.integrity@utc.edu.vn",
            "Password123!",
            "Nguyen Van G"
        );

        assertThatThrownBy(() -> localService.registerEmployee(request))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("foreign key constraint fail");
    }

    @Test
    void shouldNotOverwriteExistingUserApprovalMetadataAndStatusOnDuplicateRegistration() {
        User activeUser = new User(
            "active.emp@utc.edu.vn",
            "hashActive123",
            "Legit Employee",
            Role.EMPLOYEE,
            UserStatus.ACTIVE
        );
        activeUser.setApprovedBy(defaultAdmin);
        activeUser.setApprovedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        userRepository.save(activeUser);

        RegisterEmployeeRequest duplicateRequest = new RegisterEmployeeRequest(
            "active.emp@utc.edu.vn",
            "AttackPassword123!",
            "Attacker Name"
        );
        RegisterEmployeeResponse response = userService.registerEmployee(duplicateRequest);

        assertThat(response).isEqualTo(RegisterEmployeeResponse.defaultResponse());

        User userAfter = userRepository.findByEmail("active.emp@utc.edu.vn").orElseThrow();
        assertThat(userAfter.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(userAfter.getFullName()).isEqualTo("Legit Employee");
        assertThat(userAfter.getApprovedBy().getId()).isEqualTo(defaultAdmin.getId());
        assertThat(userAfter.getApprovedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
        assertThat(userAfter.getDisabledAt()).isNull();
        assertThat(userAfter.getPasswordHash()).isEqualTo("hashActive123");
    }

    @Test
    void shouldReturnNeutralResponseWhenEmailAlreadyExistsWithActiveDomain() {
        User existingUser = new User("existing.active@utc.edu.vn", "hashActive", "Active Domain Employee", Role.EMPLOYEE, UserStatus.PENDING);
        userRepository.save(existingUser);

        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "existing.active@utc.edu.vn",
            "NewPassword123!",
            "Another Name"
        );
        RegisterEmployeeResponse response = userService.registerEmployee(request);

        assertThat(response).isEqualTo(RegisterEmployeeResponse.defaultResponse());
    }

    @Test
    void shouldReturnNeutralResponseWhenEmailAlreadyExistsEvenIfDomainIsInactive() {
        User existingUser = new User("existing.inactive@inactive.com", "hashInactive", "Inactive Domain Employee", Role.EMPLOYEE, UserStatus.PENDING);
        userRepository.save(existingUser);

        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "existing.inactive@inactive.com",
            "NewPassword123!",
            "Another Name"
        );
        RegisterEmployeeResponse response = userService.registerEmployee(request);

        assertThat(response).isEqualTo(RegisterEmployeeResponse.defaultResponse());
    }

    @Test
    void shouldRejectNewEmailWhenDomainIsInactive() {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "brand.new@inactive.com",
            "Password123!",
            "New Inactive User"
        );

        assertThatThrownBy(() -> userService.registerEmployee(request))
            .isInstanceOf(InvalidEmailDomainException.class);

        assertThat(userRepository.findByEmail("brand.new@inactive.com")).isEmpty();
    }

    @Test
    void shouldRejectNewEmailWhenDomainIsNotRegistered() {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "brand.new@unregistered.com",
            "Password123!",
            "New Unregistered User"
        );

        assertThatThrownBy(() -> userService.registerEmployee(request))
            .isInstanceOf(InvalidEmailDomainException.class);

        assertThat(userRepository.findByEmail("brand.new@unregistered.com")).isEmpty();
    }

    @Test
    void shouldUseInjectedClockForLifecycleEventTimestamp() {
        Instant fixedInstant = Instant.parse("2026-11-07T08:30:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDateTime expectedOccurredAt = LocalDateTime.now(fixedClock);

        UserService localService = new UserServiceImpl(
            userRepository,
            allowedEmailDomainRepository,
            passwordEncoder,
            eventPublisher,
            fixedClock
        );

        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "clock.test@utc.edu.vn",
            "Password123!",
            "Clock Test User"
        );
        transactionTemplate.executeWithoutResult(status -> {
            localService.registerEmployee(request);
        });

        User user = userRepository.findByEmail("clock.test@utc.edu.vn").orElseThrow();
        Map<String, Object> audit = jdbcTemplate.queryForMap(
            "SELECT * FROM user_audit_logs WHERE user_id = ?",
            user.getId()
        );
        Object occurredAtObj = audit.get("occurred_at");
        assertThat(occurredAtObj).isNotNull();
        assertThat(occurredAtObj.toString()).startsWith(expectedOccurredAt.toLocalDate().toString());
    }

    @Test
    void shouldHandleConcurrentRegistrationSafelyAtServiceLevel() throws Exception {
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateErrorCount = new AtomicInteger(0);

        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "concurrent.service@utc.edu.vn",
            "Password123!",
            "Concurrent Service User"
        );

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    userService.registerEmployee(request);
                    successCount.incrementAndGet();
                } catch (DataIntegrityViolationException e) {
                    duplicateErrorCount.incrementAndGet();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(successCount.get() + duplicateErrorCount.get()).isEqualTo(threads);

        Integer userCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE email = 'concurrent.service@utc.edu.vn'",
            Integer.class
        );
        assertThat(userCount).isEqualTo(1);

        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_audit_logs WHERE user_id = (SELECT id FROM users WHERE email = 'concurrent.service@utc.edu.vn')",
            Integer.class
        );
        assertThat(auditCount).isEqualTo(1);
    }

    @TestConfiguration
    static class TestAuditFailureConfig {
        static volatile boolean shouldFail = false;

        @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
        @Order(Ordered.HIGHEST_PRECEDENCE)
        public void onEvent(UserLifecycleEvent event) {
            if (shouldFail) {
                throw new RuntimeException("Injected audit listener failure");
            }
        }
    }
}
