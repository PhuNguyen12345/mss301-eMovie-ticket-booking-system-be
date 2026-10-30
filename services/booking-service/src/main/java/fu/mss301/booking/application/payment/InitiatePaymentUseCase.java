package fu.mss301.booking.application.payment;

import fu.mss301.booking.application.TransactionRunner;
import fu.mss301.booking.application.port.PaymentGateway;
import fu.mss301.booking.application.port.PaymentGateway.CheckoutLink;
import fu.mss301.booking.application.port.PaymentGateway.LinkRequest;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.payment.PaymentRepository;
import fu.mss301.booking.domain.payment.PaymentTransaction;
import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;
import fu.mss301.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;

/** Opens (or re-uses) a PayOS checkout link for a pending booking. */
@Service
public class InitiatePaymentUseCase {
    /** orderCode = bookingId * 100 + attempt, so codes are unique and traceable to the booking. */
    static final int MAX_ATTEMPTS = 99;

    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final TransactionRunner tx;
    private final Clock clock;

    public InitiatePaymentUseCase(BookingRepository bookings, PaymentRepository payments, PaymentGateway gateway,
                                  TransactionRunner tx, Clock clock) {
        this.bookings = bookings;
        this.payments = payments;
        this.gateway = gateway;
        this.tx = tx;
        this.clock = clock;
    }

    public PaymentView initiate(Integer userId, Integer bookingId) {
        Draft draft = tx.call(() -> createDraft(userId, bookingId));
        if (draft.existing() != null) return draft.existing();

        CheckoutLink link;
        try {
            link = gateway.createLink(new LinkRequest(draft.orderCode(), draft.amount(), "BK" + bookingId,
                draft.expiredAt().atZone(clock.getZone()).toEpochSecond()));
        } catch (RuntimeException exception) {
            tx.run(() -> payments.findByIdForUpdate(draft.paymentId()).ifPresent(payment -> {
                if (payment.markFailed(LocalDateTime.now(clock))) payments.save(payment);
            }));
            throw exception;
        }
        return tx.call(() -> {
            var payment = payments.findByIdForUpdate(draft.paymentId()).orElseThrow();
            payment.attachCheckout(link.paymentLinkId(), link.checkoutUrl(), LocalDateTime.now(clock));
            return PaymentView.of(payments.save(payment));
        });
    }

    private Draft createDraft(Integer userId, Integer bookingId) {
        LocalDateTime now = LocalDateTime.now(clock);
        // The booking row lock serializes concurrent "pay" clicks for the same booking.
        var booking = bookings.findByIdForUpdate(bookingId).filter(b -> b.isOwnedBy(userId))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (!booking.isPayableAt(now)) {
            throw new InvalidStateTransitionException("Booking is not awaiting payment or its seat hold has expired");
        }
        var open = payments.findPendingByBookingIdForUpdate(bookingId);
        if (!open.isEmpty()) {
            var current = open.getFirst();
            if (current.getCheckoutUrl() == null) {
                throw new InvalidStateTransitionException("A payment link is already being created for this booking");
            }
            return new Draft(PaymentView.of(current), null, 0, 0, null);
        }
        long attempt = payments.countByBookingId(bookingId) + 1;
        if (attempt > MAX_ATTEMPTS) throw new InvalidStateTransitionException("Too many payment attempts for this booking");
        long amount = wholeVnd(booking.getTotalAmount());
        var payment = payments.save(PaymentTransaction.initiate(bookingId, booking.getTotalAmount(),
            bookingId * 100L + attempt, now));
        return new Draft(null, payment.getId(), payment.getOrderCode(), amount, booking.getExpiredAt());
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
