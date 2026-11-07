package fu.mss301.booking.domain.booking.event;

import fu.mss301.booking.domain.shared.DomainEvent;

public record BookingCancelled(Integer bookingId) implements DomainEvent {}
