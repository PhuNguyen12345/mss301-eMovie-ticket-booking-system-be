-- Issue #22: vouchers applied to a pending booking. Each row keeps a copy of the voucher's terms, so pricing never
-- reads the voucher again and a later change to the voucher does not move the price.
ALTER TABLE booking_seats ADD COLUMN seat_type_id INT NULL AFTER seat_type_name;

ALTER TABLE bookings
    ADD COLUMN discount_amount DECIMAL(10, 2) NOT NULL DEFAULT 0 AFTER total_amount,
    ADD CONSTRAINT chk_bookings_discount CHECK (discount_amount >= 0);

CREATE TABLE booking_vouchers (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_id INT NOT NULL,
    voucher_id INT NOT NULL,
    code VARCHAR(50) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    discount_type VARCHAR(20) NOT NULL,
    discount_value DECIMAL(10, 2) NOT NULL,
    min_order_amount DECIMAL(10, 2) NULL,
    seat_type_id INT NULL,
    combo_id INT NULL,
    seat_id INT NULL,
    expires_on DATE NOT NULL,
    discount_amount DECIMAL(10, 2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_booking_vouchers_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_booking_vouchers_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers (id),
    CONSTRAINT uq_booking_vouchers_voucher UNIQUE (booking_id, voucher_id),
    CONSTRAINT chk_booking_vouchers_discount CHECK (discount_amount >= 0),
    INDEX idx_booking_vouchers_voucher (voucher_id)
);

-- The maintenance job looks for vouchers a booking has held for too long.
CREATE INDEX idx_user_vouchers_status_updated ON user_vouchers (status, updated_at);
