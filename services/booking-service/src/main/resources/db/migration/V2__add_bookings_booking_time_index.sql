-- Serves the admin booking list: filtering by time range and sorting newest first. InnoDB secondary indexes
-- already end with the primary key, so this also covers the (booking_time, id) sort order.
CREATE INDEX idx_bookings_booking_time ON bookings (booking_time);
