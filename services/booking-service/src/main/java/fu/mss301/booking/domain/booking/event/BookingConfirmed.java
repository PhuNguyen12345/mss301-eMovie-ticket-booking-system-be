package fu.mss301.booking.domain.booking.event;

import fu.mss301.booking.domain.shared.DomainEvent;

public record BookingConfirmed(Integer bookingId) implements DomainEvent {}
