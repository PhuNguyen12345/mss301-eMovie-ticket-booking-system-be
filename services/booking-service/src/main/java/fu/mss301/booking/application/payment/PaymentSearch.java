package fu.mss301.booking.application.payment;

import fu.mss301.booking.domain.payment.PaymentStatus;

import java.time.LocalDateTime;

/**
 * Admin payment history filters; a null value means "any". {@code from} is inclusive and {@code until} exclusive,
 * both compared with when the payment was created, in the configured booking zone.
 */
public record PaymentSearch(PaymentStatus status, Integer bookingId, Long orderCode, LocalDateTime from,
                            LocalDateTime until) {}
