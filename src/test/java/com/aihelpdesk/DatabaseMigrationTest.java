package com.aihelpdesk;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class DatabaseMigrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Test
    void shouldExecuteAllFlywayMigrationsAndVerifyTables() {
        assertThat(mysql.isRunning()).isTrue();

        MigrationInfo current = flyway.info().current();
        assertThat(current).isNotNull();
        assertThat(current.getVersion().getVersion()).isEqualTo("3");
        assertThat(current.getState()).isEqualTo(MigrationState.SUCCESS);

        List<String> upperTableNames = jdbcTemplate.queryForList(
                "SELECT UPPER(table_name) FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class
        );

        assertThat(upperTableNames).contains(
                "FLYWAY_SCHEMA_HISTORY",
                "SPRING_SESSION",
                "SPRING_SESSION_ATTRIBUTES",
                "USERS",
                "ALLOWED_EMAIL_DOMAINS",
                "USER_AUDIT_LOGS"
        );

        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
                Integer.class
        );
        assertThat(migrationCount).isEqualTo(3);
    }

    @Test
    void shouldEnforceUserCheckConstraintsAndAllowValidStates() {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, full_name, role, status, approved_by, approved_at, disabled_at) " +
                        "VALUES ('admin@helpdesk.local', 'hash', 'Root Admin', 'ADMIN', 'ACTIVE', NULL, NULL, NULL)"
        );
        Long adminId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = 'admin@helpdesk.local'", Long.class);
        assertThat(adminId).isNotNull();

        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, full_name, role, status, approved_by, approved_at, disabled_at) " +
                        "VALUES ('employee@helpdesk.local', 'hash', 'Employee User', 'EMPLOYEE', 'PENDING', NULL, NULL, NULL)"
        );

        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, full_name, role, status, approved_by, approved_at, disabled_at) " +
                        "VALUES ('it@helpdesk.local', 'hash', 'IT Tech', 'IT_SUPPORT', 'ACTIVE', ?, NOW(6), NULL)",
                adminId
        );

        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, full_name, role, status, approved_by, approved_at, disabled_at) " +
                        "VALUES ('disabled@helpdesk.local', 'hash', 'Old User', 'EMPLOYEE', 'DISABLED', ?, NOW(6), NOW(6))",
                adminId
        );

        jdbcTemplate.update(
                "INSERT INTO allowed_email_domains (domain_name, active, created_by) VALUES ('helpdesk.local', TRUE, ?)",
                adminId
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, full_name, role, status, approved_by, approved_at, disabled_at) " +
                        "VALUES ('bad_role@test.local', 'hash', 'Bad Role', 'SUPER_ADMIN', 'ACTIVE', NULL, NULL, NULL)"
        )).hasMessageContaining("chk_users_role");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, full_name, role, status, approved_by, approved_at, disabled_at) " +
                        "VALUES ('bad_status@test.local', 'hash', 'Bad Status', 'EMPLOYEE', 'SUSPENDED', NULL, NULL, NULL)"
        )).hasMessageContaining("chk_users_status");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, full_name, role, status, approved_by, approved_at, disabled_at) " +
                        "VALUES ('bad_pair@test.local', 'hash', 'Bad Pair', 'EMPLOYEE', 'ACTIVE', ?, NULL, NULL)",
                adminId
        )).hasMessageContaining("chk_users_approval_pair");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, full_name, role, status, approved_by, approved_at, disabled_at) " +
                        "VALUES ('bad_pending@test.local', 'hash', 'Bad Pending', 'EMPLOYEE', 'PENDING', ?, NOW(6), NULL)",
                adminId
        )).hasMessageContaining("chk_users_pending_not_approved");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, full_name, role, status, approved_by, approved_at, disabled_at) " +
                        "VALUES ('bad_disabled@test.local', 'hash', 'Bad Disabled', 'EMPLOYEE', 'DISABLED', ?, NOW(6), NULL)",
                adminId
        )).hasMessageContaining("chk_users_disabled_at");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, full_name, role, status, approved_by, approved_at, disabled_at) " +
                        "VALUES ('bad_active@test.local', 'hash', 'Bad Active', 'EMPLOYEE', 'ACTIVE', ?, NOW(6), NOW(6))",
                adminId
        )).hasMessageContaining("chk_users_disabled_at");
    }
}
