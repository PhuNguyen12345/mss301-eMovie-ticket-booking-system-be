-- Issue #22: a code belongs to one account and is used once, so usage_limit stays unused.
ALTER TABLE vouchers
    ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'TICKET_DISCOUNT' AFTER code,
    ADD COLUMN seat_type_id INT NULL AFTER usage_limit,
    ADD COLUMN combo_id INT NULL AFTER seat_type_id,
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER status,
    ADD CONSTRAINT fk_vouchers_combo FOREIGN KEY (combo_id) REFERENCES combos (id),
    ADD CONSTRAINT chk_vouchers_kind CHECK (kind IN
        ('TICKET_EXCHANGE', 'TICKET_DISCOUNT', 'COMBO_EXCHANGE', 'COMBO_DISCOUNT', 'VALUE')),
    ADD CONSTRAINT chk_vouchers_discount_type CHECK (discount_type IN ('FIXED', 'PERCENT')),
    ADD CONSTRAINT chk_vouchers_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE vouchers ALTER COLUMN kind DROP DEFAULT;

ALTER TABLE user_vouchers
    ADD COLUMN booking_id INT NULL AFTER voucher_id,
    ADD CONSTRAINT uq_user_vouchers_voucher UNIQUE (voucher_id),
    ADD CONSTRAINT chk_user_vouchers_status CHECK (status IN ('AVAILABLE', 'RESERVED', 'USED'));
