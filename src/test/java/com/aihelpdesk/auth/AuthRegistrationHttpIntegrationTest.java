package com.aihelpdesk.auth;

import com.aihelpdesk.user.AllowedEmailDomain;
import com.aihelpdesk.user.AllowedEmailDomainRepository;
import com.aihelpdesk.user.Role;
import com.aihelpdesk.user.User;
import com.aihelpdesk.user.UserRepository;
import com.aihelpdesk.user.UserStatus;
import com.aihelpdesk.user.dto.RegisterEmployeeRequest;
import com.aihelpdesk.user.dto.RegisterEmployeeResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthRegistrationHttpIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AllowedEmailDomainRepository allowedEmailDomainRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User adminUser;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM user_audit_logs");
        jdbcTemplate.update("DELETE FROM allowed_email_domains");
        jdbcTemplate.update("UPDATE users SET approved_by = NULL, approved_at = NULL");
        jdbcTemplate.update("DELETE FROM users");

        adminUser = new User("admin.system@utc.edu.vn", "hashAdmin123", "System Admin", Role.ADMIN, UserStatus.ACTIVE);
        userRepository.save(adminUser);

        AllowedEmailDomain activeDomain = new AllowedEmailDomain("utc.edu.vn", true, adminUser);
        allowedEmailDomainRepository.save(activeDomain);

        AllowedEmailDomain inactiveDomain = new AllowedEmailDomain("inactive.com", false, adminUser);
        allowedEmailDomainRepository.save(inactiveDomain);
    }

    @Test
    void regHttp01Through06ShouldRegisterEmployeeSuccessfullyAndPersistPendingUserWithAudit() throws Exception {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "employee.valid@utc.edu.vn",
            "ValidPassword123!",
            "Nguyen Van A"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.message").value(RegisterEmployeeResponse.NEUTRAL_MESSAGE))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.role").doesNotExist())
            .andExpect(jsonPath("$.status").doesNotExist())
            .andExpect(jsonPath("$.id").doesNotExist());

        User user = userRepository.findByEmail("employee.valid@utc.edu.vn").orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(user.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(user.getApprovedBy()).isNull();
        assertThat(user.getApprovedAt()).isNull();
        assertThat(user.getDisabledAt()).isNull();
        assertThat(user.getPasswordHash()).startsWith("$2a$12$");
        assertThat(passwordEncoder.matches("ValidPassword123!", user.getPasswordHash())).isTrue();

        Map<String, Object> audit = jdbcTemplate.queryForMap(
            "SELECT * FROM user_audit_logs WHERE user_id = ?",
            user.getId()
        );
        assertThat(audit.get("event_type")).isEqualTo("USER_REGISTERED");
        assertThat(audit.get("actor_type")).isEqualTo("SYSTEM");
        assertThat(audit.get("to_status")).isEqualTo("PENDING");
        assertThat(audit.get("from_status")).isNull();
        assertThat(audit.get("actor_id")).isNull();

        Integer totalAuditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_audit_logs",
            Integer.class
        );
        assertThat(totalAuditCount).isEqualTo(1);
    }

    @Test
    void regHttp07Through10ShouldHandleDuplicateEmailWithIdenticalResponseAndNoModification() throws Exception {
        User existingUser = new User(
            "duplicate.test@utc.edu.vn",
            passwordEncoder.encode("ExistingPass123!"),
            "Original Employee",
            Role.EMPLOYEE,
            UserStatus.PENDING
        );
        userRepository.save(existingUser);

        long userCountBefore = userRepository.count();

        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "duplicate.test@utc.edu.vn",
            "NewPassword999!",
            "Attacker Changing Name"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.message").value(RegisterEmployeeResponse.NEUTRAL_MESSAGE));

        assertThat(userRepository.count()).isEqualTo(userCountBefore);

        User unmodifiedUser = userRepository.findByEmail("duplicate.test@utc.edu.vn").orElseThrow();
        assertThat(unmodifiedUser.getFullName()).isEqualTo("Original Employee");
        assertThat(passwordEncoder.matches("ExistingPass123!", unmodifiedUser.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("NewPassword999!", unmodifiedUser.getPasswordHash())).isFalse();

        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_audit_logs",
            Integer.class
        );
        assertThat(auditCount).isZero();
    }

    @Test
    void regHttp10bShouldHandleDuplicateEmailWithInactiveDomainNeutrally() throws Exception {
        User existingUser = new User(
            "legacy@inactive.com",
            passwordEncoder.encode("LegacyPass123!"),
            "Legacy Employee",
            Role.EMPLOYEE,
            UserStatus.PENDING
        );
        userRepository.save(existingUser);

        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "legacy@inactive.com",
            "Password123!",
            "Duplicate User"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.message").value(RegisterEmployeeResponse.NEUTRAL_MESSAGE));

        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_audit_logs",
            Integer.class
        );
        assertThat(auditCount).isZero();
    }

    @Test
    void regHttp11ShouldRejectMissingEmail() throws Exception {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            null,
            "ValidPassword123!",
            "Nguyen Van A"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.details").isArray());

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void regHttp12ShouldRejectMalformedEmail() throws Exception {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "not-an-email",
            "ValidPassword123!",
            "Nguyen Van A"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void regHttp13ShouldRejectPasswordUnder8CodePoints() throws Exception {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "test.pass@utc.edu.vn",
            "Short1!",
            "Nguyen Van A"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void regHttp14ShouldRejectPasswordExceeding72Utf8Bytes() throws Exception {
        String longPassword = "A".repeat(73);
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "test.pass@utc.edu.vn",
            longPassword,
            "Nguyen Van A"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void regHttp15ShouldRejectFullNameUnder2Characters() throws Exception {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "test.name@utc.edu.vn",
            "ValidPassword123!",
            "A"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void regHttp16ShouldRejectFullNameExceeding150Characters() throws Exception {
        String longName = "A".repeat(151);
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "test.name@utc.edu.vn",
            "ValidPassword123!",
            longName
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void regHttp17ShouldRejectMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid-json-body}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"))
            .andExpect(jsonPath("$.message").value("Malformed request body or invalid JSON format"));

        assertThat(userRepository.count()).isEqualTo(1);
        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_audit_logs",
            Integer.class
        );
        assertThat(auditCount).isZero();
    }

    @Test
    void regHttp18ShouldRejectUnlistedDomain() throws Exception {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "user@unlisted.com",
            "ValidPassword123!",
            "Nguyen Van A"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_EMAIL_DOMAIN"));

        assertThat(userRepository.findByEmail("user@unlisted.com")).isEmpty();
    }

    @Test
    void regHttp19ShouldRejectInactiveDomainForNewUser() throws Exception {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "new.user@inactive.com",
            "ValidPassword123!",
            "Nguyen Van A"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_EMAIL_DOMAIN"));

        assertThat(userRepository.findByEmail("new.user@inactive.com")).isEmpty();
    }

    @Test
    void regHttp20ShouldRejectUnregisteredSubdomain() throws Exception {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "student@fit.utc.edu.vn",
            "ValidPassword123!",
            "Nguyen Van A"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_EMAIL_DOMAIN"));

        assertThat(userRepository.findByEmail("student@fit.utc.edu.vn")).isEmpty();
    }

    @Test
    void regHttp21ShouldRejectPostWithoutCsrfToken() throws Exception {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "employee.csrf@utc.edu.vn",
            "ValidPassword123!",
            "Nguyen Van A"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void regHttp22ShouldRejectPostWithInvalidCsrfToken() throws Exception {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "employee.csrf@utc.edu.vn",
            "ValidPassword123!",
            "Nguyen Van A"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .header("X-XSRF-TOKEN", "invalid-csrf-token")
                .cookie(new Cookie("XSRF-TOKEN", "different-cookie-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void regHttp23And24ShouldAcceptPostWithHttpLevelCookieAndHeaderRoundTrip() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("XSRF-TOKEN"))
            .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
            .andExpect(jsonPath("$.parameterName").value("_csrf"))
            .andExpect(jsonPath("$.token").doesNotExist())
            .andReturn();

        MockHttpServletResponse csrfResponse = csrfResult.getResponse();
        Cookie xsrfCookie = csrfResponse.getCookie("XSRF-TOKEN");
        assertThat(xsrfCookie).isNotNull();
        String csrfToken = xsrfCookie.getValue();
        assertThat(csrfToken).isNotBlank();

        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "csrf.roundtrip@utc.edu.vn",
            "ValidPassword123!",
            "CSRF User"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .cookie(xsrfCookie)
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.message").value(RegisterEmployeeResponse.NEUTRAL_MESSAGE));
    }

    @Test
    void regHttp25ShouldAllowPublicRegistrationWithoutAuthentication() throws Exception {
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
            "public.unauth@utc.edu.vn",
            "ValidPassword123!",
            "Unauthenticated User"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isAccepted());

        assertThat(userRepository.findByEmail("public.unauth@utc.edu.vn")).isPresent();
    }
}
