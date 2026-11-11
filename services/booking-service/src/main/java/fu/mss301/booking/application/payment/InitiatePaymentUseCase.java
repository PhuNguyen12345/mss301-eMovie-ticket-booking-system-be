package fu.mss301.booking.application.payment;

import java.util.Map;
import fu.mss301.booking.domain.shared.ErrorCode;
import fu.mss301.booking.application.exception.BookingNotFoundException;
import fu.mss301.booking.application.port.DomainEvents;
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.application.port.PaymentGateway;
import fu.mss301.booking.application.port.PaymentGateway.CheckoutLink;
import fu.mss301.booking.application.port.PaymentGateway.LinkRequest;
import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.payment.PaymentRepository;
import fu.mss301.booking.domain.payment.PaymentTransaction;
import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Opens (or re-uses) a PayOS checkout link for a pending booking. When vouchers cover the whole amount the
 * booking is confirmed straight away and no link is created.
 */
public class InitiatePaymentUseCase {
    /** orderCode = bookingId * 100 + attempt, so codes are unique and traceable to the booking. */
    static final int MAX_ATTEMPTS = 99;

    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final Transactions transactions;
    private final DomainEvents events;
    private final Clock clock;

    public InitiatePaymentUseCase(BookingRepository bookings, PaymentRepository payments, PaymentGateway gateway,
                                  Transactions transactions, DomainEvents events, Clock clock) {
        this.bookings = bookings;
        this.payments = payments;
        this.gateway = gateway;
        this.transactions = transactions;
        this.events = events;
        this.clock = clock;
    }

    public PaymentView initiate(Integer userId, Integer bookingId) {
        transactions.run(() -> removeExpiredVouchers(userId, bookingId));
        Draft draft = transactions.write(() -> createDraft(userId, bookingId));
        if (draft.existing() != null) return draft.existing();

        CheckoutLink link;
        try {
            link = gateway.createLink(new LinkRequest(draft.orderCode(), draft.amount(), "BK" + bookingId,
                draft.expiredAt().atZone(clock.getZone()).toEpochSecond()));
        } catch (RuntimeException exception) {
            transactions.run(() -> payments.findByIdForUpdate(draft.paymentId()).ifPresent(payment -> {
                if (payment.markFailed(LocalDateTime.now(clock))) payments.save(payment);
            }));
            throw exception;
        }
        return transactions.write(() -> {
            var payment = payments.findByIdForUpdate(draft.paymentId()).orElseThrow();
            payment.attachCheckout(link.paymentLinkId(), link.checkoutUrl(), LocalDateTime.now(clock));
            return PaymentView.of(payments.save(payment));
        });
    }

    /** A voucher whose last day has passed comes off before the price goes to PayOS; an open link keeps its price. */
    private void removeExpiredVouchers(Integer userId, Integer bookingId) {
        LocalDateTime now = LocalDateTime.now(clock);
        var booking = bookings.findByIdForUpdate(bookingId).filter(b -> b.isOwnedBy(userId))
            .orElseThrow(BookingNotFoundException::new);
        if (!booking.isPayableAt(now) || !booking.hasExpiredVoucherOn(now.toLocalDate())) return;
        if (!payments.findPendingByBookingIdForUpdate(bookingId).isEmpty()) return;
        booking.removeExpiredVouchers(now);
        bookings.save(booking);
        events.publish(booking.pullEvents());
    }

    private Draft createDraft(Integer userId, Integer bookingId) {
        LocalDateTime now = LocalDateTime.now(clock);
        // The booking row lock serializes concurrent "pay" clicks for the same booking.
        var booking = bookings.findByIdForUpdate(bookingId).filter(b -> b.isOwnedBy(userId))
            .orElseThrow(BookingNotFoundException::new);
        if (!booking.isPayableAt(now)) {
            throw new InvalidStateTransitionException(ErrorCode.BOOKING_NOT_PENDING,
                "Booking is not awaiting payment or its seat hold has expired");
        }
        var open = payments.findPendingByBookingIdForUpdate(bookingId);
        if (!open.isEmpty()) {
            var current = open.getFirst();
            if (current.checkoutUrl() == null) {
                throw new InvalidStateTransitionException(ErrorCode.PAYMENT_IN_PROGRESS,
                    "A payment link is already being created for this booking");
            }
            return new Draft(PaymentView.of(current), null, 0, 0, null);
        }
        if (booking.hasExpiredVoucherOn(now.toLocalDate())) {
            // A voucher expired at midnight between the two steps; this transaction must not change the booking.
            throw new InvalidStateTransitionException(ErrorCode.VOUCHER_EXPIRED,
                "A voucher on this booking has just expired, please try again");
        }
        if (!booking.hasPayableAmount()) {
            // Reached when combos change after vouchers were applied, or when a showtime is priced below it.
            throw new DomainException(ErrorCode.VOUCHER_MIN_PAYMENT, "The amount to pay must be 0 or at least "
                + Booking.MIN_PAYMENT.toPlainString() + " VND, please change the vouchers or combos",
                Map.of("minPayment", Booking.MIN_PAYMENT));
        }
        if (booking.totalAmount().signum() == 0) {
            booking.confirm(now);
            bookings.save(booking);
            events.publish(booking.pullEvents());
            return new Draft(PaymentView.notRequired(bookingId), null, 0, 0, null);
        }
        long attempt = payments.countByBookingId(bookingId) + 1;
        if (attempt > MAX_ATTEMPTS) {
            throw new InvalidStateTransitionException(ErrorCode.PAYMENT_ATTEMPTS_EXCEEDED,
                "Too many payment attempts for this booking");
        }
        long amount = wholeVnd(booking.totalAmount());
        var payment = payments.save(PaymentTransaction.initiate(bookingId, booking.totalAmount(),
            bookingId * 100L + attempt, now));
        return new Draft(null, payment.id(), payment.orderCode(), amount, booking.expiredAt());
    }

    private static long wholeVnd(BigDecimal amount) {
        try {
            return amount.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException exception) {
            throw new DomainException("PayOS only accepts whole VND amounts");
        }
    }

    private record Draft(PaymentView existing, Integer paymentId, long orderCode, long amount, LocalDateTime expiredAt) {}
}
