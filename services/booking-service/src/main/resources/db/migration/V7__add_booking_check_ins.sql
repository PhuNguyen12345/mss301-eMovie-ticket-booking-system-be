-- Issue #25: scans at the cinema door. One booking code covers every seat; a group arriving apart is let in a few
-- people at a time, so each scan records how many went in, when, and which staff account scanned it.
CREATE TABLE booking_check_ins (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_id INT NOT NULL,
    quantity INT NOT NULL,
    checked_in_by INT NOT NULL,
    checked_in_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_booking_check_ins_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT chk_booking_check_ins_quantity CHECK (quantity > 0),
    INDEX idx_booking_check_ins_booking (booking_id)
);
