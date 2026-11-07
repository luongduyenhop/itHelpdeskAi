package com.aihelpdesk.audit;

import com.aihelpdesk.user.event.UserLifecycleEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class UserLifecycleAuditListener {

    private final UserAuditLogRepository auditLogRepository;

    public UserLifecycleAuditListener(UserAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onUserLifecycleEvent(UserLifecycleEvent event) {
        UserAuditLog auditLog = new UserAuditLog(
            event.eventId(),
            event.userId(),
            event.actorId(),
            event.actorType(),
            event.eventType(),
            event.fromStatus(),
            event.toStatus(),
            event.reason(),
            event.occurredAt()
        );
        auditLogRepository.save(auditLog);
    }
}
