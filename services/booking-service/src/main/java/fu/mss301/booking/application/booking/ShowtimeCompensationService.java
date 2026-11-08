package fu.mss301.booking.application.booking;

import fu.mss301.booking.application.port.ShowtimeCatalog;
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.application.voucher.ValueVoucherIssuer;
import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.domain.booking.BookingVoucher;
import fu.mss301.booking.domain.booking.ShowtimeCompensation;
import fu.mss301.booking.domain.payment.PaymentRepository;
import fu.mss301.booking.domain.payment.PaymentStatus;
import fu.mss301.booking.domain.payment.PaymentTransaction;
import fu.mss301.booking.domain.payment.VoucherShare;
import fu.mss301.booking.domain.voucher.DiscountType;
import fu.mss301.booking.domain.voucher.VoucherKind;
import fu.mss301.booking.domain.voucher.VoucherRepository;
import fu.mss301.booking.domain.voucher.VoucherSourceReason;
import fu.mss301.booking.domain.voucher.VoucherTerms;

import java.lang.System.Logger.Level;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Compensates the customer of a paid booking whose showtime the cinema cancelled. Every step runs in its own
 * transaction and can be repeated: vouchers are issued once per booking and reason, the payment is given back once,
 * and the booking is marked compensated last, so the maintenance job finishes a run that stopped half way.
 */
public class ShowtimeCompensationService {
    private static final System.Logger log = System.getLogger(ShowtimeCompensationService.class.getName());
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final VoucherRepository vouchers;
    private final ValueVoucherIssuer issuer;
    private final ShowtimeCatalog showtimes;
    private final Transactions transactions;
    private final Duration shortNotice;
    private final Clock clock;

    /** @param shortNotice cancelling closer than this to the start earns the customer an apology */
    public ShowtimeCompensationService(BookingRepository bookings, PaymentRepository payments,
                                       VoucherRepository vouchers, ValueVoucherIssuer issuer,
                                       ShowtimeCatalog showtimes, Transactions transactions, Duration shortNotice,
                                       Clock clock) {
        this.bookings = bookings;
        this.payments = payments;
        this.vouchers = vouchers;
        this.issuer = issuer;
        this.showtimes = showtimes;
        this.transactions = transactions;
        this.shortNotice = shortNotice;
        this.clock = clock;
    }

    public void compensate(Integer bookingId) {
        Booking booking = transactions.read(() -> bookings.findById(bookingId)).orElse(null);
        if (booking == null || booking.status() != BookingStatus.SHOWTIME_CANCELLED || booking.compensatedAt() != null) {
            return;
        }
        // The copied ticket fixes the start; only bookings older than that copy ask showtime-service.
        LocalDateTime start = booking.ticket() != null ? booking.ticket().startTime()
            : showtimes.getShowtime(booking.showtimeId()).startTime();
        var payment = transactions.read(() -> keptPayment(bookingId));
        var plan = ShowtimeCompensation.of(booking, payment.map(PaymentTransaction::amount).orElse(BigDecimal.ZERO),
            booking.wasCancelledAtShortNotice(start, shortNotice));

        booking.vouchers().forEach(used -> settleUsedVoucher(used, bookingId));
        var shares = issueVouchers(booking, plan);
        payment.filter(paid -> !shares.isEmpty()).ifPresent(paid -> giveBack(paid.id(), shares, booking));
        transactions.run(() -> bookings.findByIdForUpdate(bookingId).ifPresent(locked -> {
            if (locked.markCompensated(LocalDateTime.now(clock))) bookings.save(locked);
        }));
    }

    /** Safety net for a lost event or a run that stopped half way. */
    public void compensatePending(int batchSize) {
        for (Integer bookingId : transactions.read(() -> bookings.findUncompensatedIds(batchSize))) {
            try {
                compensate(bookingId);
            } catch (RuntimeException exception) {
                log.log(Level.WARNING, () -> "Could not compensate booking " + bookingId + ": " + exception.getMessage());
            }
        }
    }

    /**
     * Ticket and combo vouchers go back to the customer. A value voucher stays used, because a remainder may have
     * been issued from it; what it covered is in the value refund instead.
     */
    private void settleUsedVoucher(BookingVoucher used, Integer bookingId) {
        transactions.run(() -> vouchers.findByIdForUpdate(used.voucherId()).ifPresent(voucher -> {
            LocalDateTime now = LocalDateTime.now(clock);
            boolean changed = used.kind() == VoucherKind.VALUE
                ? voucher.markUsedBy(bookingId, now) : voucher.giveBackFrom(bookingId, now);
            if (changed) vouchers.save(voucher);
        }));
    }

    /** @return the shares of the payment the issued vouchers give back */
    private List<VoucherShare> issueVouchers(Booking booking, ShowtimeCompensation plan) {
        Integer owner = booking.userId();
        Integer id = booking.id();
        var shares = new ArrayList<VoucherShare>();
        if (plan.comboRefund().signum() > 0) {
            var voucher = issuer.forBooking(id, VoucherSourceReason.SHOWTIME_COMBO_REFUND, owner,
                (first, last) -> comboDiscount(plan.comboRefund(), first, last));
            shares.add(new VoucherShare(voucher.id(), plan.comboRefund()));
        }
        if (plan.valueRefund().signum() > 0) {
            var voucher = issuer.forBooking(id, VoucherSourceReason.SHOWTIME_REFUND, owner,
                (first, last) -> new VoucherTerms(VoucherKind.VALUE, DiscountType.FIXED, plan.valueRefund(), first,
                    last, null, null, null));
            if (plan.moneyRefund().signum() > 0) shares.add(new VoucherShare(voucher.id(), plan.moneyRefund()));
        }
        if (plan.freeTicket()) {
            issuer.forBooking(id, VoucherSourceReason.APOLOGY_TICKET, owner,
                (first, last) -> new VoucherTerms(VoucherKind.TICKET_EXCHANGE, null, null, first, last, null, null,
                    null));
        }
        if (plan.apologyCombo().signum() > 0) {
            issuer.forBooking(id, VoucherSourceReason.APOLOGY_COMBO, owner,
                (first, last) -> comboDiscount(plan.apologyCombo(), first, last));
        }
        return shares;
    }

    private void giveBack(Integer paymentId, List<VoucherShare> shares, Booking booking) {
        transactions.run(() -> payments.findByIdForUpdate(paymentId).ifPresent(locked -> {
            if (locked.status() == PaymentStatus.SUCCESS && locked.compensateWithVouchers(shares,
                    "Showtime of booking " + booking.bookingCode().value() + " was cancelled, given back as vouchers",
                    LocalDateTime.now(clock))) {
                payments.save(locked);
            }
        }));
    }

    /** The payment that paid for the booking: the first that succeeded, even if this service already gave it back. */
    private Optional<PaymentTransaction> keptPayment(Integer bookingId) {
        return payments.findByBookingId(bookingId).stream()
            .filter(p -> p.status() == PaymentStatus.SUCCESS || p.status() == PaymentStatus.REFUNDED)
            .min(Comparator.comparing(PaymentTransaction::id));
    }

    private static VoucherTerms comboDiscount(BigDecimal amount, LocalDate first, LocalDate last) {
        return new VoucherTerms(VoucherKind.COMBO_DISCOUNT, DiscountType.FIXED, amount, first, last, null, null, null);
    }
}
