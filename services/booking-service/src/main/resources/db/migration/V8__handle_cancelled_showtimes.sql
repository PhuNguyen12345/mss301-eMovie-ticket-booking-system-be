-- Issue #20: when the cinema cancels a showtime, its paid bookings become SHOWTIME_CANCELLED and the customer is
-- compensated with vouchers. compensated_at marks that compensation finished, so the maintenance job can finish
-- a run that stopped half way.
ALTER TABLE bookings
    ADD COLUMN showtime_cancelled_at DATETIME(6) NULL AFTER expired_at,
    ADD COLUMN compensated_at DATETIME(6) NULL AFTER showtime_cancelled_at,
    DROP CHECK chk_bookings_status,
    ADD CONSTRAINT chk_bookings_status
        CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'EXPIRED', 'SHOWTIME_CANCELLED')),
    ADD INDEX idx_bookings_showtime_status (showtime_id, status);

-- A booking can now be owed several vouchers, one per reason. Remainder vouchers issued so far keep their meaning.
ALTER TABLE vouchers ADD COLUMN source_reason VARCHAR(30) NULL AFTER source_booking_id;
UPDATE vouchers SET source_reason = 'REMAINDER' WHERE source_booking_id IS NOT NULL;
ALTER TABLE vouchers
    ADD CONSTRAINT uq_vouchers_source_booking_reason UNIQUE (source_booking_id, source_reason),
    DROP INDEX uq_vouchers_source_booking,
    ADD CONSTRAINT chk_vouchers_source_reason CHECK (source_reason IN
        ('REMAINDER', 'SHOWTIME_REFUND', 'SHOWTIME_COMBO_REFUND', 'APOLOGY_TICKET', 'APOLOGY_COMBO'));
