package fu.mss301.booking.domain.booking;

/** Read model: a seat that is currently HELD or BOOKED on a showtime. */
public record OccupiedSeat(Integer seatId, SeatHoldStatus status) {}
