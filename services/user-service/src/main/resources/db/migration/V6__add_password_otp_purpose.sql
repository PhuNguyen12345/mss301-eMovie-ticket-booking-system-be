ALTER TABLE password_reset_otps
    ADD COLUMN purpose ENUM('PASSWORD_CHANGE', 'PASSWORD_RESET') NOT NULL DEFAULT 'PASSWORD_CHANGE';

CREATE INDEX idx_password_reset_otps_user_purpose_created
    ON password_reset_otps (user_id, purpose, created_at, id);
