-- Ticket details copied at seat hold, so a sold ticket never changes with the catalog and stays readable while
-- other services are down. Columns are nullable because bookings made before this migration have no copy.
ALTER TABLE bookings
    ADD COLUMN movie_title VARCHAR(255) NULL,
    ADD COLUMN movie_poster_url VARCHAR(500) NULL,
    ADD COLUMN start_time DATETIME(6) NULL,
    ADD COLUMN end_time DATETIME(6) NULL,
    ADD COLUMN cinema_name VARCHAR(255) NULL,
    ADD COLUMN screen_name VARCHAR(100) NULL;

ALTER TABLE booking_seats
    ADD COLUMN row_code VARCHAR(10) NULL,
    ADD COLUMN seat_number INT NULL,
    ADD COLUMN seat_type_name VARCHAR(50) NULL;

ALTER TABLE booking_combos ADD COLUMN combo_name VARCHAR(100) NULL;

UPDATE booking_combos bc
JOIN combos c ON c.id = bc.combo_id
SET bc.combo_name = c.name;

ALTER TABLE booking_combos MODIFY COLUMN combo_name VARCHAR(100) NOT NULL;
