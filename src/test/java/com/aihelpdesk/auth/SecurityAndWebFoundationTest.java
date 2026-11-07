package com.aihelpdesk.auth;

import com.aihelpdesk.user.AllowedEmailDomain;
import com.aihelpdesk.user.AllowedEmailDomainRepository;
import com.aihelpdesk.user.Role;
import com.aihelpdesk.user.User;
import com.aihelpdesk.user.UserRepository;
import com.aihelpdesk.user.UserStatus;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
class SecurityAndWebFoundationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AllowedEmailDomainRepository allowedEmailDomainRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM user_audit_logs");
        jdbcTemplate.update("DELETE FROM allowed_email_domains");
        jdbcTemplate.update("UPDATE users SET approved_by = NULL, approved_at = NULL");
        jdbcTemplate.update("DELETE FROM users");

        User admin = new User("admin.sec@utc.edu.vn", "hashAdmin123", "Sec Admin", Role.ADMIN, UserStatus.ACTIVE);
        userRepository.save(admin);

        AllowedEmailDomain domain = new AllowedEmailDomain("utc.edu.vn", true, admin);
        allowedEmailDomainRepository.save(domain);
    }

    @Test
    void shouldAllowPublicAccessToCsrfEndpointAndMaterializeCookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("XSRF-TOKEN"))
            .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
            .andExpect(jsonPath("$.parameterName").value("_csrf"))
            .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void shouldRejectPostRegisterWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DirtiesContext
    void shouldAcceptPostRegisterWithMockMvcCsrf() throws Exception {
        String validJson = "{\"email\":\"sec.user@utc.edu.vn\",\"password\":\"Password123!\",\"fullName\":\"Sec User\"}";
        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validJson))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void shouldAcceptPostRegisterWithHttpLevelCookieAndHeaderRoundTrip() throws Exception {
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

        String validJson = "{\"email\":\"roundtrip@utc.edu.vn\",\"password\":\"Password123!\",\"fullName\":\"Roundtrip User\"}";
        mockMvc.perform(post("/api/v1/auth/register")
                .cookie(xsrfCookie)
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validJson))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void shouldRejectProtectedEndpointsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isForbidden());
    }

    @Test
    void shouldConfigureBcryptPasswordEncoder() {
        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
        String raw = "StrongPassword123!";
        String encoded = passwordEncoder.encode(raw);
        assertThat(encoded).startsWith("$2a$12$");
        assertThat(passwordEncoder.matches(raw, encoded)).isTrue();
        assertThat(passwordEncoder.matches("WrongPassword", encoded)).isFalse();
    }
}
