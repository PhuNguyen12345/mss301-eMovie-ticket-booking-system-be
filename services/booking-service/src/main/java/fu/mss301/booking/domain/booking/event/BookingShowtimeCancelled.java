package fu.mss301.booking.domain.booking.event;

import fu.mss301.booking.domain.shared.DomainEvent;

/** The cinema cancelled the showtime of a paid booking; the customer is owed compensation. */
public record BookingShowtimeCancelled(Integer bookingId) implements DomainEvent {}
