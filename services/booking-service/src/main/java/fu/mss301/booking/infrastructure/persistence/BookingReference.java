package fu.mss301.booking.infrastructure.persistence;

/** What payment history shows of a booking. */
record BookingReference(Integer id, String bookingCode, Integer userId) {}
