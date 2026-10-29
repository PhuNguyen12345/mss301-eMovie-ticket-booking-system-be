CREATE TABLE bookings (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    showtime_id BIGINT NOT NULL,
    booking_code VARCHAR(50) NOT NULL,
    booking_time DATETIME(6) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expired_at DATETIME(6) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_bookings_code UNIQUE (booking_code),
    CONSTRAINT chk_bookings_amount CHECK (total_amount >= 0),
    CONSTRAINT chk_bookings_status CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    INDEX idx_bookings_status_expiry (status, expired_at),
    INDEX idx_bookings_user_created (user_id, created_at),
    INDEX idx_bookings_showtime (showtime_id)
);

-- active_flag is 1 while a seat is HELD/BOOKED and NULL once RELEASED. MySQL lets NULLs repeat in a
-- unique index, so this index allows exactly one active hold per (showtime, seat) and any number of
-- released history rows.
CREATE TABLE booking_seats (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_id INT NOT NULL,
    showtime_id BIGINT NOT NULL,
    showtime_seat_id INT NULL,
    seat_id INT NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    active_flag TINYINT GENERATED ALWAYS AS (IF(status IN ('HELD', 'BOOKED'), 1, NULL)) STORED,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_booking_seats_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT uq_booking_seats_active UNIQUE (showtime_id, seat_id, active_flag),
    CONSTRAINT chk_booking_seats_price CHECK (price >= 0),
    CONSTRAINT chk_booking_seats_status CHECK (status IN ('HELD', 'BOOKED', 'RELEASED')),
    INDEX idx_booking_seats_booking (booking_id)
);

CREATE TABLE payment_transactions (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_id INT NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    order_code BIGINT NOT NULL,
    payment_link_id VARCHAR(255) NULL,
    provider_transaction_id VARCHAR(255) NULL,
    checkout_url VARCHAR(500) NULL,
    amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    paid_at DATETIME(6) NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_payment_transactions_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT uq_payment_transactions_order_code UNIQUE (order_code),
    CONSTRAINT chk_payment_transactions_amount CHECK (amount >= 0),
    CONSTRAINT chk_payment_transactions_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED')),
    INDEX idx_payment_transactions_booking (booking_id),
    INDEX idx_payment_transactions_status (status, updated_at)
);

CREATE TABLE refunds (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payment_id INT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    reason VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL,
    refunded_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payment_transactions (id),
    CONSTRAINT chk_refunds_amount CHECK (amount >= 0),
    CONSTRAINT chk_refunds_status CHECK (status IN ('PENDING', 'COMPLETED', 'REJECTED')),
    INDEX idx_refunds_payment (payment_id)
);

CREATE TABLE combos (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    price DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT chk_combos_price CHECK (price >= 0)
);

CREATE TABLE booking_combos (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_id INT NOT NULL,
    combo_id INT NOT NULL,
    quantity INT NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    CONSTRAINT fk_booking_combos_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_booking_combos_combo FOREIGN KEY (combo_id) REFERENCES combos (id),
    CONSTRAINT chk_booking_combos_quantity CHECK (quantity > 0),
    CONSTRAINT chk_booking_combos_price CHECK (price >= 0)
);

CREATE TABLE vouchers (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    discount_type VARCHAR(20) NOT NULL,
    discount_value DECIMAL(10, 2) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    min_order_amount DECIMAL(10, 2) NULL,
    usage_limit INT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT uq_vouchers_code UNIQUE (code),
    CONSTRAINT chk_vouchers_dates CHECK (end_date >= start_date)
);

CREATE TABLE user_vouchers (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    voucher_id INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT fk_user_vouchers_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers (id),
    INDEX idx_user_vouchers_user (user_id)
);
