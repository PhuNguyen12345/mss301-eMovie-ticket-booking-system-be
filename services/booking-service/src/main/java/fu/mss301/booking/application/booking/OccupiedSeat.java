package fu.mss301.booking.application.booking;

import fu.mss301.booking.domain.booking.SeatHoldStatus;

/** Read model: a seat that is currently HELD or BOOKED on a showtime. */
public record OccupiedSeat(Integer seatId, SeatHoldStatus status) {}
