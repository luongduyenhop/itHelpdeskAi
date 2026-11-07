package com.aihelpdesk.user.event;

import com.aihelpdesk.audit.ActorType;
import com.aihelpdesk.user.UserStatus;

import java.time.LocalDateTime;
import java.util.Objects;

public record UserLifecycleEvent(
    String eventId,
    Long userId,
    Long actorId,
    ActorType actorType,
    UserLifecycleEventType eventType,
    UserStatus fromStatus,
    UserStatus toStatus,
    LocalDateTime occurredAt,
    String reason
) {

    public UserLifecycleEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(actorType, "actorType must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(toStatus, "toStatus must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
