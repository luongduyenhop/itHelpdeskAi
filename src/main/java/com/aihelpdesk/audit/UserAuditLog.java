package com.aihelpdesk.audit;

import com.aihelpdesk.user.UserStatus;
import com.aihelpdesk.user.event.UserLifecycleEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "user_audit_logs")
public class UserAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "actor_id")
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private ActorType actorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private UserLifecycleEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private UserStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private UserStatus toStatus;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public UserAuditLog() {
    }

    public UserAuditLog(
        String eventId,
        Long userId,
        Long actorId,
        ActorType actorType,
        UserLifecycleEventType eventType,
        UserStatus fromStatus,
        UserStatus toStatus,
        String reason,
        LocalDateTime occurredAt
    ) {
        this.eventId = eventId;
        this.userId = userId;
        this.actorId = actorId;
        this.actorType = actorType;
        this.eventType = eventType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getActorId() {
        return actorId;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public UserLifecycleEventType getEventType() {
        return eventType;
    }

    public UserStatus getFromStatus() {
        return fromStatus;
    }

    public UserStatus getToStatus() {
        return toStatus;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserAuditLog that = (UserAuditLog) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    @Override
    public String toString() {
        return "UserAuditLog{" +
            "id=" + id +
            ", eventId='" + eventId + '\'' +
            ", userId=" + userId +
            ", actorId=" + actorId +
            ", actorType=" + actorType +
            ", eventType=" + eventType +
            ", fromStatus=" + fromStatus +
            ", toStatus=" + toStatus +
            ", occurredAt=" + occurredAt +
            ", createdAt=" + createdAt +
            '}';
    }
}
