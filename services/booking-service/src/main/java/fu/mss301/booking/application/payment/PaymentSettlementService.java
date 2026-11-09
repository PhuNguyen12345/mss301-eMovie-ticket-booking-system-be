package fu.mss301.booking.application.payment;

import fu.mss301.booking.application.port.DomainEvents;
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.application.voucher.ValueVoucherIssuer;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.domain.payment.PaymentRepository;
import fu.mss301.booking.domain.payment.PaymentStatus;
import fu.mss301.booking.domain.payment.PaymentTransaction;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;

/**
 * Applies a provider-confirmed payment, then brings the booking in line with it. Each step touches one
 * aggregate in its own transaction; the booking step is driven by the PaymentSucceeded event.
 */
public class PaymentSettlementService {
    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final Transactions transactions;
    private final DomainEvents events;
    private final ValueVoucherIssuer valueVouchers;
    private final Clock clock;

    public PaymentSettlementService(PaymentRepository payments, BookingRepository bookings, Transactions transactions,
                                    DomainEvents events, ValueVoucherIssuer valueVouchers, Clock clock) {
        this.payments = payments;
        this.bookings = bookings;
        this.transactions = transactions;
        this.events = events;
        this.valueVouchers = valueVouchers;
        this.clock = clock;
    }

    /** @return false when no payment has this order code (e.g. PayOS' webhook URL test call) */
    public boolean recordSuccess(long orderCode, long paidAmount, String reference, LocalDateTime paidAt) {
        return transactions.write(() -> payments.findByOrderCodeForUpdate(orderCode).map(payment -> {
            LocalDateTime now = LocalDateTime.now(clock);
            if (payment.markSucceeded(BigDecimal.valueOf(paidAmount), reference, paidAt == null ? now : paidAt, now)) {
                payments.save(payment);
                events.publish(payment.pullEvents());
            }
            return true;
        }).orElse(false));
    }

    /**
     * Confirms the booking for a successful payment. If the booking can no longer be confirmed (expired,
     * cancelled, or already paid by another attempt) the money is given back to the customer as a value voucher.
     * Each step runs in its own transaction and can be repeated, so the reconciliation job finishes a run that
     * stopped half way.
     */
    public void settle(Integer paymentId, Integer bookingId) {
        Integer owner = transactions.write(() -> {
            var booking = bookings.findByIdForUpdate(bookingId).orElseThrow(
                () -> new IllegalStateException("Payment " + paymentId + " references missing booking " + bookingId));
            if (booking.status() == BookingStatus.PENDING_PAYMENT) {
                booking.confirm(LocalDateTime.now(clock));
                bookings.save(booking);
                events.publish(booking.pullEvents());
                return null;
            }
            boolean kept = booking.status() == BookingStatus.CONFIRMED && isFirstSuccessfulPayment(paymentId, bookingId);
            return kept ? null : booking.userId();
        });
        if (owner != null) giveBack(paymentId, bookingId, owner);
    }

    private void giveBack(Integer paymentId, Integer bookingId, Integer owner) {
        var payment = transactions.read(() -> payments.findByBookingId(bookingId).stream()
            .filter(p -> p.id().equals(paymentId)).findFirst()).orElse(null);
        // A payment refunded earlier, by voucher or by hand, keeps that refund.
        if (payment == null || !payment.refunds().isEmpty() || payment.status() != PaymentStatus.SUCCESS) return;
        var voucher = valueVouchers.forPayment(paymentId, owner, payment.amount());
        transactions.run(() -> payments.findByIdForUpdate(paymentId).ifPresent(locked -> {
            if (locked.compensateWithVoucher(voucher.id(), "Booking " + bookingId
                    + " could not be confirmed with this payment, given back as voucher " + voucher.code().value(),
                    LocalDateTime.now(clock))) {
                payments.save(locked);
            }
        }));
    }

    private boolean isFirstSuccessfulPayment(Integer paymentId, Integer bookingId) {
        return payments.findByBookingId(bookingId).stream()
            .filter(p -> p.status() == PaymentStatus.SUCCESS || p.status() == PaymentStatus.REFUNDED)
            .min(Comparator.comparing(PaymentTransaction::id))
            .map(p -> p.id().equals(paymentId)).orElse(true);
    }
}
