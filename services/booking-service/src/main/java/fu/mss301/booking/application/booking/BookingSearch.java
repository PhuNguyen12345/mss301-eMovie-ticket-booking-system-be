package fu.mss301.booking.application.booking;

import fu.mss301.booking.domain.booking.BookingStatus;

import java.time.LocalDateTime;

/**
 * Admin booking filters; a null value means "any". {@code from} is inclusive and {@code to} exclusive, both
 * compared with the booking time in the configured booking zone.
 */
public record BookingSearch(BookingStatus status, Long showtimeId, Integer userId, String code, LocalDateTime from,
                            LocalDateTime to) {}
