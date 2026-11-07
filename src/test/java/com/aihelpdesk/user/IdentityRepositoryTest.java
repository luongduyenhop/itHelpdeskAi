package com.aihelpdesk.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class IdentityRepositoryTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AllowedEmailDomainRepository domainRepository;

    @BeforeEach
    void cleanUp() {
        domainRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldFindUserByEmailAndCheckExistence() {
        User user = new User("employee@utc.edu.vn", "hashedPassword", "Nguyen Van A", Role.EMPLOYEE, UserStatus.PENDING);
        userRepository.save(user);

        Optional<User> found = userRepository.findByEmail("employee@utc.edu.vn");
        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("Nguyen Van A");

        assertThat(userRepository.existsByEmail("employee@utc.edu.vn")).isTrue();
        assertThat(userRepository.existsByEmail("other@utc.edu.vn")).isFalse();
    }

    @Test
    void shouldFindUserByIdAndStatus() {
        User activeUser = new User("it@utc.edu.vn", "hashedPassword", "IT Staff", Role.IT_SUPPORT, UserStatus.ACTIVE);
        userRepository.save(activeUser);

        Optional<User> foundActive = userRepository.findByIdAndStatus(activeUser.getId(), UserStatus.ACTIVE);
        assertThat(foundActive).isPresent();
        assertThat(foundActive.get().getEmail()).isEqualTo("it@utc.edu.vn");

        Optional<User> notFoundPending = userRepository.findByIdAndStatus(activeUser.getId(), UserStatus.PENDING);
        assertThat(notFoundPending).isEmpty();
    }

    @Test
    void shouldFindPendingUsersOrderedByCreatedAtAsc() {
        User user1 = new User("pending1@utc.edu.vn", "hash1", "Pending One", Role.EMPLOYEE, UserStatus.PENDING);
        user1.setCreatedAt(LocalDateTime.now().minusHours(2));
        userRepository.save(user1);

        User user2 = new User("pending2@utc.edu.vn", "hash2", "Pending Two", Role.EMPLOYEE, UserStatus.PENDING);
        user2.setCreatedAt(LocalDateTime.now().minusHours(1));
        userRepository.save(user2);

        User activeUser = new User("active@utc.edu.vn", "hash3", "Active One", Role.EMPLOYEE, UserStatus.ACTIVE);
        userRepository.save(activeUser);

        List<User> pendingUsers = userRepository.findByStatusOrderByCreatedAtAsc(UserStatus.PENDING);
        assertThat(pendingUsers).hasSize(2);
        assertThat(pendingUsers.get(0).getEmail()).isEqualTo("pending1@utc.edu.vn");
        assertThat(pendingUsers.get(1).getEmail()).isEqualTo("pending2@utc.edu.vn");
    }

    @Test
    void shouldPerformExactDomainLookupAndFilterInactive() {
        User admin = new User("admin@utc.edu.vn", "hashAdmin", "Admin User", Role.ADMIN, UserStatus.ACTIVE);
        userRepository.save(admin);

        AllowedEmailDomain activeDomain = new AllowedEmailDomain("utc.edu.vn", true, admin);
        domainRepository.save(activeDomain);

        AllowedEmailDomain inactiveDomain = new AllowedEmailDomain("old.edu.vn", false, admin);
        domainRepository.save(inactiveDomain);

        Optional<AllowedEmailDomain> foundActive = domainRepository.findByDomainNameAndActiveTrue("utc.edu.vn");
        assertThat(foundActive).isPresent();
        assertThat(foundActive.get().getDomainName()).isEqualTo("utc.edu.vn");

        Optional<AllowedEmailDomain> foundInactiveAsActive = domainRepository.findByDomainNameAndActiveTrue("old.edu.vn");
        assertThat(foundInactiveAsActive).isEmpty();

        assertThat(domainRepository.existsByDomainName("utc.edu.vn")).isTrue();
        assertThat(domainRepository.existsByDomainName("nonexistent.edu.vn")).isFalse();
    }

    @Test
    void shouldNotMatchSubdomainsForExactDomainLookup() {
        User admin = new User("admin@utc.edu.vn", "hashAdmin", "Admin User", Role.ADMIN, UserStatus.ACTIVE);
        userRepository.save(admin);

        AllowedEmailDomain activeDomain = new AllowedEmailDomain("utc.edu.vn", true, admin);
        domainRepository.save(activeDomain);

        assertThat(domainRepository.findByDomainName("student.utc.edu.vn")).isEmpty();
        assertThat(domainRepository.findByDomainNameAndActiveTrue("student.utc.edu.vn")).isEmpty();
        assertThat(domainRepository.existsByDomainName("student.utc.edu.vn")).isFalse();
    }
}
