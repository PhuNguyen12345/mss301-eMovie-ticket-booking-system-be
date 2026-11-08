package fu.mss301.booking.application.voucher;

import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.domain.voucher.VoucherKind;
import fu.mss301.booking.domain.voucher.VoucherRepository;

import java.lang.System.Logger.Level;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Keeps a voucher in line with the booking that held it: a paid booking uses it up, any other outcome gives it
 * back. The booking is the record of what happened, so this can run any number of times and in any order.
 * A value voucher that was not used up leaves its owner a new voucher for the rest.
 */
public class VoucherReservations {
    private static final System.Logger log = System.getLogger(VoucherReservations.class.getName());
    private final VoucherRepository vouchers;
    private final BookingRepository bookings;
    private final Transactions transactions;
    private final ValueVoucherIssuer valueVouchers;
    private final Duration staleAfter;
    private final Clock clock;

    /** @param staleAfter how long a voucher may stay held before the maintenance job checks it */
    public VoucherReservations(VoucherRepository vouchers, BookingRepository bookings, Transactions transactions,
                               ValueVoucherIssuer valueVouchers, Duration staleAfter, Clock clock) {
        this.vouchers = vouchers;
        this.bookings = bookings;
        this.transactions = transactions;
        this.valueVouchers = valueVouchers;
        this.staleAfter = staleAfter;
        this.clock = clock;
    }

    public void settle(Integer voucherId, Integer bookingId) {
        issueRemainder(voucherId, bookingId);
        transactions.run(() -> vouchers.findByIdForUpdate(voucherId).ifPresent(voucher -> {
            LocalDateTime now = LocalDateTime.now(clock);
            Booking booking = bookings.findById(bookingId).orElse(null);
            boolean holds = booking != null && booking.hasVoucher(voucherId);
            boolean changed;
            if (holds && booking.status() == BookingStatus.CONFIRMED) {
                changed = voucher.markUsedBy(bookingId, now);
            } else if (holds && booking.status() == BookingStatus.PENDING_PAYMENT) {
                changed = false;
            } else {
                changed = voucher.releaseFrom(bookingId, now);
            }
            if (changed) vouchers.save(voucher);
        }));
    }

    /**
     * Runs before the voucher is marked used, so if it fails the voucher stays held and the maintenance job
     * tries again. Issuing is idempotent per booking.
     */
    private void issueRemainder(Integer voucherId, Integer bookingId) {
        var rest = transactions.read(() -> bookings.findById(bookingId)
            .filter(booking -> booking.status() == BookingStatus.CONFIRMED)
            .flatMap(booking -> booking.vouchers().stream()
                .filter(voucher -> voucher.voucherId().equals(voucherId) && voucher.kind() == VoucherKind.VALUE)
                .findFirst()
                .map(voucher -> new Rest(booking.userId(),
                    voucher.discountValue().subtract(voucher.discountAmount()).setScale(0, RoundingMode.DOWN)))));
        rest.filter(left -> left.amount().signum() > 0)
            .ifPresent(left -> valueVouchers.forRemainder(bookingId, left.owner(), left.amount()));
    }

    private record Rest(Integer owner, BigDecimal amount) {}

    /**
     * Safety net for a lost event or a crash between the two steps of applying a voucher.
     *
     * @return how many vouchers were checked
     */
    public int settleStale(int batchSize) {
        LocalDateTime before = LocalDateTime.now(clock).minus(staleAfter);
        var ids = transactions.read(() -> vouchers.findReservedSince(before, batchSize));
        for (Integer voucherId : ids) {
            try {
                Integer bookingId = transactions.read(() -> vouchers.findById(voucherId)
                    .map(voucher -> voucher.claim().bookingId()).orElse(null));
                if (bookingId != null) settle(voucherId, bookingId);
            } catch (RuntimeException exception) {
                log.log(Level.WARNING, () -> "Could not settle voucher " + voucherId + ": " + exception.getMessage());
            }
        }
        return ids.size();
    }
}
