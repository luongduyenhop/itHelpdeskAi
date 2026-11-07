CREATE TABLE user_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    actor_id BIGINT NULL,
    actor_type VARCHAR(20) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_user_audit_logs PRIMARY KEY (id),
    CONSTRAINT uk_user_audit_logs_event_id UNIQUE (event_id),
    CONSTRAINT fk_user_audit_logs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_audit_logs_actor FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_user_audit_logs_actor_type CHECK (actor_type IN ('USER', 'SYSTEM')),
    CONSTRAINT chk_user_audit_logs_actor_consistency CHECK (
        (actor_type = 'SYSTEM' AND actor_id IS NULL)
        OR
        (actor_type = 'USER' AND actor_id IS NOT NULL)
    ),
    CONSTRAINT chk_user_audit_logs_event_type CHECK (
        event_type IN ('USER_REGISTERED', 'USER_CREATED_BY_ADMIN', 'USER_APPROVED', 'USER_REJECTED', 'USER_DISABLED', 'USER_REACTIVATED')
    ),
    CONSTRAINT chk_user_audit_logs_from_status CHECK (
        from_status IS NULL OR from_status IN ('PENDING', 'ACTIVE', 'DISABLED')
    ),
    CONSTRAINT chk_user_audit_logs_to_status CHECK (
        to_status IN ('PENDING', 'ACTIVE', 'DISABLED')
    ),
    INDEX idx_user_audit_logs_user_occurred_at (user_id, occurred_at),
    INDEX idx_user_audit_logs_actor_occurred_at (actor_id, occurred_at),
    INDEX idx_user_audit_logs_event_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
