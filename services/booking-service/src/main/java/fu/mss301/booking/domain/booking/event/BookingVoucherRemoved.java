package fu.mss301.booking.domain.booking.event;

import fu.mss301.booking.domain.shared.DomainEvent;

/** A voucher left a pending booking, by the customer or because it no longer applies, so it can be used again. */
public record BookingVoucherRemoved(Integer bookingId, Integer voucherId) implements DomainEvent {}
