package com.aihelpdesk.user.service;

import com.aihelpdesk.audit.ActorType;
import com.aihelpdesk.common.exception.InvalidEmailDomainException;
import com.aihelpdesk.user.AllowedEmailDomainRepository;
import com.aihelpdesk.user.Role;
import com.aihelpdesk.user.User;
import com.aihelpdesk.user.UserRepository;
import com.aihelpdesk.user.UserStatus;
import com.aihelpdesk.user.dto.RegisterEmployeeRequest;
import com.aihelpdesk.user.dto.RegisterEmployeeResponse;
import com.aihelpdesk.user.event.UserLifecycleEvent;
import com.aihelpdesk.user.event.UserLifecycleEventType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final AllowedEmailDomainRepository allowedEmailDomainRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public UserServiceImpl(
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

    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public RegisterEmployeeResponse registerEmployee(RegisterEmployeeRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(normalizedEmail)) {
            return RegisterEmployeeResponse.defaultResponse();
        }

        String domain = extractDomain(normalizedEmail);
        allowedEmailDomainRepository.findByDomainNameAndActiveTrue(domain)
            .orElseThrow(() -> new InvalidEmailDomainException("Email domain is not allowed or inactive: " + domain));

        String passwordHash = passwordEncoder.encode(request.password());
        User user = new User(
            normalizedEmail,
            passwordHash,
            request.fullName().trim(),
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

    private String extractDomain(String email) {
        int atIndex = email.lastIndexOf('@');
        if (atIndex == -1 || atIndex == email.length() - 1) {
            throw new InvalidEmailDomainException("Invalid email format: " + email);
        }
        return email.substring(atIndex + 1);
    }
}
