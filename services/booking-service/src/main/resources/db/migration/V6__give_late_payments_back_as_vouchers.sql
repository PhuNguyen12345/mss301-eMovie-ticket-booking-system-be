-- Issue #22: money that cannot confirm its booking is given back as a value voucher instead of a manual transfer.
-- Each source issues at most one voucher, so a retried settlement finds the voucher it already issued.
ALTER TABLE vouchers
    ADD COLUMN source_payment_id INT NULL AFTER combo_id,
    ADD COLUMN source_booking_id INT NULL AFTER source_payment_id,
    ADD CONSTRAINT uq_vouchers_source_payment UNIQUE (source_payment_id),
    ADD CONSTRAINT uq_vouchers_source_booking UNIQUE (source_booking_id),
    ADD CONSTRAINT fk_vouchers_source_payment FOREIGN KEY (source_payment_id) REFERENCES payment_transactions (id),
    ADD CONSTRAINT fk_vouchers_source_booking FOREIGN KEY (source_booking_id) REFERENCES bookings (id);

-- Refunds recorded before this migration were manual transfers.
ALTER TABLE refunds
    ADD COLUMN method VARCHAR(20) NOT NULL DEFAULT 'MANUAL' AFTER reason,
    ADD COLUMN voucher_id INT NULL AFTER method,
    ADD CONSTRAINT fk_refunds_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers (id),
    ADD CONSTRAINT chk_refunds_method CHECK (method IN ('MANUAL', 'VOUCHER'));

ALTER TABLE refunds ALTER COLUMN method DROP DEFAULT;
