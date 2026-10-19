CREATE TABLE users (
    id INT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(20) NULL,
    password_hash VARCHAR(255) NOT NULL,
    date_of_birth DATE NULL,
    gender VARCHAR(20) NULL,
    city_province VARCHAR(100) NULL,
    district VARCHAR(100) NULL,
    address VARCHAR(500) NULL,
    preferred_cinema_id INT NULL,
    avatar_url VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL,
    email_verified_at DATETIME(6) NULL,
    last_login_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_phone UNIQUE (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE roles (
    id INT NOT NULL AUTO_INCREMENT,
    name ENUM('ADMIN', 'CUSTOMER') NOT NULL,
    description VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_roles (
    user_id INT NOT NULL,
    role_id INT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_roles_role_id (role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE password_reset_otps (
    id INT NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_password_reset_otps_user_id (user_id),
    CONSTRAINT fk_password_reset_otps_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refresh_tokens (
    id INT NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    token_hash VARCHAR(500) NOT NULL,
    device_name VARCHAR(255) NULL,
    browser VARCHAR(100) NULL,
    ip_address VARCHAR(45) NULL,
    location VARCHAR(255) NULL,
    last_active_at DATETIME(6) NULL,
    expires_at DATETIME(6) NOT NULL,
    is_revoked BIT(1) NOT NULL DEFAULT b'0',
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    KEY idx_refresh_tokens_user_id (user_id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE login_histories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    login_at DATETIME(6) NOT NULL,
    ip_address VARCHAR(45) NULL,
    location VARCHAR(255) NULL,
    user_agent VARCHAR(500) NULL,
    result VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_login_histories_user_id (user_id),
    CONSTRAINT fk_login_histories_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_activity_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    action VARCHAR(100) NOT NULL,
    details VARCHAR(1000) NULL,
    ip_address VARCHAR(45) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_user_activity_logs_user_id (user_id),
    CONSTRAINT fk_user_activity_logs_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_two_factor_auth (
    id INT NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    method VARCHAR(20) NOT NULL,
    secret_encrypted VARCHAR(500) NOT NULL,
    enabled BIT(1) NOT NULL DEFAULT b'0',
    enabled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_two_factor_auth_user_id UNIQUE (user_id),
    CONSTRAINT fk_user_two_factor_auth_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
