package fu.mss301.booking.application.booking;

import fu.mss301.booking.application.exception.BookingNotFoundException;
import fu.mss301.booking.application.exception.VoucherNotFoundException;
import fu.mss301.booking.application.port.DomainEvents;
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.application.voucher.VoucherReservations;
import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.booking.VoucherOffer;
import fu.mss301.booking.domain.payment.PaymentRepository;
import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;
import fu.mss301.booking.domain.voucher.Voucher;
import fu.mss301.booking.domain.voucher.VoucherCode;
import fu.mss301.booking.domain.voucher.VoucherRepository;

import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Applies a customer's voucher to their pending booking, or takes it off again. The voucher and the booking are
 * changed in separate transactions: first the voucher is held for the booking, then the booking is priced. If
 * the second step fails the voucher is given back.
 */
public class ApplyVoucherUseCase {
    private static final System.Logger log = System.getLogger(ApplyVoucherUseCase.class.getName());
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final VoucherRepository vouchers;
    private final VoucherReservations reservations;
    private final Transactions transactions;
    private final DomainEvents events;
    private final Clock clock;

    public ApplyVoucherUseCase(BookingRepository bookings, PaymentRepository payments, VoucherRepository vouchers,
                               VoucherReservations reservations, Transactions transactions, DomainEvents events,
                               Clock clock) {
        this.bookings = bookings;
        this.payments = payments;
        this.vouchers = vouchers;
        this.reservations = reservations;
        this.transactions = transactions;
        this.events = events;
        this.clock = clock;
    }

    /** @param seatId the seat a ticket exchange voucher pays for; null for other kinds */
    public BookingView apply(Integer userId, Integer bookingId, String code, Integer seatId) {
        if (code == null || code.isBlank()) throw new DomainException("Voucher code is required");
        // Checked first, so a request that cannot succeed never holds the voucher.
        transactions.read(() -> {
            var booking = bookings.findById(bookingId).filter(b -> b.isOwnedBy(userId))
                .orElseThrow(BookingNotFoundException::new);
            if (!booking.isPayableAt(LocalDateTime.now(clock))) {
                throw new InvalidStateTransitionException("Booking is not awaiting payment or its seat hold has expired");
            }
            return null;
        });

        VoucherOffer offer = transactions.write(() -> {
            // Someone else's code answers like an unknown one, so guessing reveals nothing.
            var voucher = vouchers.findByCodeForUpdate(VoucherCode.normalize(code)).filter(v -> v.isOwnedBy(userId))
                .orElseThrow(VoucherNotFoundException::new);
            voucher.reserveFor(userId, bookingId, LocalDateTime.now(clock));
            return offerOf(vouchers.save(voucher));
        });

        try {
            return transactions.write(() -> {
                var booking = editable(userId, bookingId);
                booking.applyVoucher(offer, seatId, LocalDateTime.now(clock));
                return BookingView.of(bookings.save(booking), null);
            });
        } catch (RuntimeException exception) {
            giveBack(offer.voucherId(), bookingId, exception);
            throw exception;
        }
    }

    public BookingView remove(Integer userId, Integer bookingId, Integer voucherId) {
        return transactions.write(() -> {
            var booking = editable(userId, bookingId);
            if (!booking.hasVoucher(voucherId)) throw new VoucherNotFoundException();
            booking.removeVoucher(voucherId, LocalDateTime.now(clock));
            var saved = bookings.save(booking);
            events.publish(booking.pullEvents());
            return BookingView.of(saved, null);
        });
    }

    /** The booking row lock serializes this with InitiatePaymentUseCase and combo changes. */
    private Booking editable(Integer userId, Integer bookingId) {
        var booking = bookings.findByIdForUpdate(bookingId).filter(b -> b.isOwnedBy(userId))
            .orElseThrow(BookingNotFoundException::new);
        // An open PayOS link carries the old amount, and a different paid amount would be rejected.
        if (!payments.findPendingByBookingIdForUpdate(bookingId).isEmpty()) {
            throw new InvalidStateTransitionException("Vouchers cannot be changed after a payment link was created");
        }
        return booking;
    }

    /** Gives the voucher back unless the booking holds it after all; the maintenance job retries a failure. */
    private void giveBack(Integer voucherId, Integer bookingId, RuntimeException cause) {
        try {
            reservations.settle(voucherId, bookingId);
        } catch (RuntimeException exception) {
            cause.addSuppressed(exception);
            log.log(Level.WARNING, () -> "Could not give voucher " + voucherId + " back: " + exception.getMessage());
        }
    }

    private static VoucherOffer offerOf(Voucher voucher) {
        var terms = voucher.terms();
        return new VoucherOffer(voucher.id(), voucher.code().value(), terms.kind(), terms.discountType(),
            terms.discountValue(), terms.minOrderAmount(), terms.seatTypeId(), terms.comboId(), terms.endDate());
    }
}
