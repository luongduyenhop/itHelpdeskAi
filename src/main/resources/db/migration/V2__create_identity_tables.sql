CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    role VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    approved_by BIGINT NULL,
    approved_at DATETIME(6) NULL,
    disabled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_approved_by FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_users_role CHECK (role IN ('EMPLOYEE', 'IT_SUPPORT', 'ADMIN')),
    CONSTRAINT chk_users_status CHECK (status IN ('PENDING', 'ACTIVE', 'DISABLED')),
    CONSTRAINT chk_users_approval_pair CHECK (
        (approved_by IS NULL AND approved_at IS NULL)
        OR
        (approved_by IS NOT NULL AND approved_at IS NOT NULL)
    ),
    CONSTRAINT chk_users_pending_not_approved CHECK (
        status <> 'PENDING'
        OR (approved_by IS NULL AND approved_at IS NULL)
    ),
    CONSTRAINT chk_users_disabled_at CHECK (
        (status = 'DISABLED' AND disabled_at IS NOT NULL)
        OR
        (status <> 'DISABLED' AND disabled_at IS NULL)
    ),
    INDEX idx_users_status_created_at (status, created_at),
    INDEX idx_users_role_status (role, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE allowed_email_domains (
    id BIGINT NOT NULL AUTO_INCREMENT,
    domain_name VARCHAR(253) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_allowed_email_domains PRIMARY KEY (id),
    CONSTRAINT uk_allowed_email_domains_domain UNIQUE (domain_name),
    CONSTRAINT fk_allowed_email_domains_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_allowed_email_domains_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
