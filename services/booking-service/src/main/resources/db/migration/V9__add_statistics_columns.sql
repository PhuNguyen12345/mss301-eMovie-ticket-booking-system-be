-- Movie and cinema ids on the ticket copy, so statistics group by id instead of by a title that two movies can
-- share. Null until filled in: new bookings get them at seat hold, older ones from the ticket backfill job.
ALTER TABLE bookings
    ADD COLUMN movie_id INT NULL AFTER showtime_id,
    ADD COLUMN cinema_id INT NULL AFTER movie_id,
    ADD INDEX idx_bookings_status_start (status, start_time);
