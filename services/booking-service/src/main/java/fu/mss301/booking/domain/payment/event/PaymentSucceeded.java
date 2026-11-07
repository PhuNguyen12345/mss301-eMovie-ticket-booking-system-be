package fu.mss301.booking.domain.payment.event;

import fu.mss301.booking.domain.shared.DomainEvent;

public record PaymentSucceeded(Integer paymentId, Integer bookingId) implements DomainEvent {}
