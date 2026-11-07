package com.aihelpdesk.auth;

import com.aihelpdesk.audit.ActorType;
import com.aihelpdesk.common.exception.InvalidEmailDomainException;
import com.aihelpdesk.user.AllowedEmailDomain;
import com.aihelpdesk.user.AllowedEmailDomainRepository;
import com.aihelpdesk.user.Role;
import com.aihelpdesk.user.User;
import com.aihelpdesk.user.UserRepository;
import com.aihelpdesk.user.UserStatus;
import com.aihelpdesk.user.dto.RegisterEmployeeResponse;
import com.aihelpdesk.user.event.UserLifecycleEvent;
import com.aihelpdesk.user.event.UserLifecycleEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext
@Import(DuplicateKeyTranslationHttpIntegrationTest.TestControllerConfig.class)
class DuplicateKeyTranslationHttpIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AllowedEmailDomainRepository allowedEmailDomainRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestConstraintService testConstraintService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM user_audit_logs");
        jdbcTemplate.update("DELETE FROM allowed_email_domains");
        jdbcTemplate.update("UPDATE users SET approved_by = NULL, approved_at = NULL");
        jdbcTemplate.update("DELETE FROM users");

        testConstraintService.reset();

        User admin = new User("admin.test@utc.edu.vn", "hashAdmin123", "Admin", Role.ADMIN, UserStatus.ACTIVE);
        userRepository.save(admin);

        AllowedEmailDomain domain = new AllowedEmailDomain("utc.edu.vn", true, admin);
        allowedEmailDomainRepository.save(domain);
    }

    @Test
    void shouldTranslateExactUkUsersEmailViolationTo202AcceptedWithIdenticalBody() throws Exception {
        User existingUser = new User("collision@utc.edu.vn", "originalHash", "Original User", Role.EMPLOYEE, UserStatus.PENDING);
        userRepository.save(existingUser);

        mockMvc.perform(post("/test/constraints/duplicate-email")
                .param("email", "collision@utc.edu.vn")
                .with(csrf())
                .with(user("adminUser").roles("ADMIN")))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.message").value(RegisterEmployeeResponse.NEUTRAL_MESSAGE));

        Integer userCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE email = 'collision@utc.edu.vn'",
            Integer.class
        );
        assertThat(userCount).isEqualTo(1);

        User userInDb = userRepository.findByEmail("collision@utc.edu.vn").orElseThrow();
        assertThat(userInDb.getFullName()).isEqualTo("Original User");
        assertThat(userInDb.getPasswordHash()).isEqualTo("originalHash");

        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_audit_logs",
            Integer.class
        );
        assertThat(auditCount).isZero();
    }

    @Test
    void shouldNotTranslateOtherConstraintViolationsTo202() throws Exception {
        mockMvc.perform(post("/test/constraints/check-constraint")
                .param("email", "check.fail@utc.edu.vn")
                .with(csrf())
                .with(user("adminUser").roles("ADMIN")))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.errorCode").value("DATABASE_INTEGRITY_ERROR"))
            .andExpect(jsonPath("$.message").value("A database integrity error occurred"));

        Integer userCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE email = 'check.fail@utc.edu.vn'",
            Integer.class
        );
        assertThat(userCount).isZero();
    }

    @Test
    void shouldConfirmTransactionRollbackBeforeResponse() throws Exception {
        User existingUser = new User("rollback.check@utc.edu.vn", "origHash", "Pre-existing User", Role.EMPLOYEE, UserStatus.PENDING);
        userRepository.save(existingUser);

        long initialUserCount = userRepository.count();

        mockMvc.perform(post("/test/constraints/duplicate-email")
                .param("email", "rollback.check@utc.edu.vn")
                .with(csrf())
                .with(user("adminUser").roles("ADMIN")))
            .andExpect(status().isAccepted());

        assertThat(userRepository.count()).isEqualTo(initialUserCount);
        User verifiedUser = userRepository.findByEmail("rollback.check@utc.edu.vn").orElseThrow();
        assertThat(verifiedUser.getFullName()).isEqualTo("Pre-existing User");
    }

    @Test
    void shouldHandleConcurrentRegistrationRaceConditionWhereBothPassExistsByEmail() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<MvcResult> request1 = () -> mockMvc.perform(post("/test/constraints/concurrent-register")
                    .param("email", "concurrent.race@utc.edu.vn")
                    .with(csrf())
                    .with(user("adminUser").roles("ADMIN")))
                .andReturn();

            Callable<MvcResult> request2 = () -> mockMvc.perform(post("/test/constraints/concurrent-register")
                    .param("email", "concurrent.race@utc.edu.vn")
                    .with(csrf())
                    .with(user("adminUser").roles("ADMIN")))
                .andReturn();

            Future<MvcResult> future1 = executor.submit(request1);
            Future<MvcResult> future2 = executor.submit(request2);

            MvcResult result1 = future1.get(10, TimeUnit.SECONDS);
            MvcResult result2 = future2.get(10, TimeUnit.SECONDS);

            assertThat(testConstraintService.getPassedExistsCount()).isEqualTo(2);

            assertThat(result1.getResponse().getStatus()).isEqualTo(202);
            assertThat(result2.getResponse().getStatus()).isEqualTo(202);

            assertThat(result1.getResponse().getContentAsString()).contains(RegisterEmployeeResponse.NEUTRAL_MESSAGE);
            assertThat(result2.getResponse().getContentAsString()).contains(RegisterEmployeeResponse.NEUTRAL_MESSAGE);

            Integer userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = 'concurrent.race@utc.edu.vn'",
                Integer.class
            );
            assertThat(userCount).isEqualTo(1);

            Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_audit_logs",
                Integer.class
            );
            assertThat(auditCount).isEqualTo(1);

            Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT * FROM user_audit_logs"
            );
            assertThat(audit.get("event_type")).isEqualTo("USER_REGISTERED");
            assertThat(audit.get("actor_type")).isEqualTo("SYSTEM");
        } finally {
            executor.shutdown();
        }
    }

    @TestConfiguration
    static class TestControllerConfig {

        @Bean
        public TestConstraintService testConstraintService(
            UserRepository userRepository,
            AllowedEmailDomainRepository allowedEmailDomainRepository,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            Clock clock
        ) {
            return new TestConstraintService(
                userRepository,
                allowedEmailDomainRepository,
                passwordEncoder,
                eventPublisher,
                clock
            );
        }

        @Bean
        public TestConstraintController testConstraintController(TestConstraintService testConstraintService) {
            return new TestConstraintController(testConstraintService);
        }
    }

    public static class TestConstraintService {
        private final UserRepository userRepository;
        private final AllowedEmailDomainRepository allowedEmailDomainRepository;
        private final PasswordEncoder passwordEncoder;
        private final ApplicationEventPublisher eventPublisher;
        private final Clock clock;
        private final CyclicBarrier barrier = new CyclicBarrier(2);
        private final AtomicInteger passedExistsCount = new AtomicInteger(0);

        public TestConstraintService(
            UserRepository userRepository,
            AllowedEmailDomainRepository allowedEmailDomainRepository,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            Clock clock
        ) {
            this.userRepository = userRepository;
            this.allowedEmailDomainRepository = allowedEmailDomainRepository;
            this.passwordEncoder = passwordEncoder;
            this.eventPublisher = eventPublisher;
            this.clock = clock;
        }

        public void reset() {
            barrier.reset();
            passedExistsCount.set(0);
        }

        public int getPassedExistsCount() {
            return passedExistsCount.get();
        }

        @Transactional
        public void triggerDuplicateEmail(String email) {
            User user = new User(email, "collidingHash", "Colliding User", Role.EMPLOYEE, UserStatus.PENDING);
            userRepository.save(user);
        }

        @Transactional
        public void triggerCheckConstraint(String email) {
            User user = new User(email, "hashedPassword", "Check User", Role.EMPLOYEE, UserStatus.ACTIVE);
            user.setApprovedAt(LocalDateTime.now());
            userRepository.save(user);
        }

        @Transactional
        public RegisterEmployeeResponse registerWithConcurrentRace(String email) {
            String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
            boolean exists = userRepository.existsByEmail(normalizedEmail);
            if (!exists) {
                passedExistsCount.incrementAndGet();
            }

            try {
                barrier.await(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if (exists) {
                return RegisterEmployeeResponse.defaultResponse();
            }

            String domain = normalizedEmail.substring(normalizedEmail.lastIndexOf('@') + 1);
            allowedEmailDomainRepository.findByDomainNameAndActiveTrue(domain)
                .orElseThrow(() -> new InvalidEmailDomainException("Email domain is not allowed or inactive: " + domain));

            String passwordHash = passwordEncoder.encode("Password123!");
            User user = new User(
                normalizedEmail,
                passwordHash,
                "Concurrent User",
                Role.EMPLOYEE,
                UserStatus.PENDING
            );
            User savedUser = userRepository.save(user);

            UserLifecycleEvent event = new UserLifecycleEvent(
                UUID.randomUUID().toString(),
                savedUser.getId(),
                null,
                ActorType.SYSTEM,
                UserLifecycleEventType.USER_REGISTERED,
                null,
                UserStatus.PENDING,
                LocalDateTime.now(clock),
                "Employee self-registration"
            );
            eventPublisher.publishEvent(event);

            return RegisterEmployeeResponse.defaultResponse();
        }
    }

    @RestController
    @RequestMapping("/test/constraints")
    public static class TestConstraintController {
        private final TestConstraintService testConstraintService;

        public TestConstraintController(TestConstraintService testConstraintService) {
            this.testConstraintService = testConstraintService;
        }

        @PostMapping("/duplicate-email")
        public ResponseEntity<?> triggerDuplicate(@RequestParam("email") String email) {
            testConstraintService.triggerDuplicateEmail(email);
            return ResponseEntity.ok().build();
        }

        @PostMapping("/check-constraint")
        public ResponseEntity<?> triggerCheck(@RequestParam("email") String email) {
            testConstraintService.triggerCheckConstraint(email);
            return ResponseEntity.ok().build();
        }

        @PostMapping("/concurrent-register")
        public ResponseEntity<?> triggerConcurrentRegister(@RequestParam("email") String email) {
            RegisterEmployeeResponse response = testConstraintService.registerWithConcurrentRace(email);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }
    }
}
