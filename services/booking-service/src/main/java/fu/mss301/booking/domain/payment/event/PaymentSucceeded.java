package fu.mss301.booking.domain.payment.event;

public record PaymentSucceeded(Integer paymentId, Integer bookingId) {}
