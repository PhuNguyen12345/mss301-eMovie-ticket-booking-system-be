package fu.mss301.booking.application.voucher;

import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.domain.voucher.VoucherRepository;

import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Keeps a voucher in line with the booking that held it: a paid booking uses it up, any other outcome gives it
 * back. The booking is the record of what happened, so this can run any number of times and in any order.
 */
public class VoucherReservations {
    private static final System.Logger log = System.getLogger(VoucherReservations.class.getName());
    private final VoucherRepository vouchers;
    private final BookingRepository bookings;
    private final Transactions transactions;
    private final Duration staleAfter;
    private final Clock clock;

    /** @param staleAfter how long a voucher may stay held before the maintenance job checks it */
    public VoucherReservations(VoucherRepository vouchers, BookingRepository bookings, Transactions transactions,
                               Duration staleAfter, Clock clock) {
        this.vouchers = vouchers;
        this.bookings = bookings;
        this.transactions = transactions;
        this.staleAfter = staleAfter;
        this.clock = clock;
    }

    public void settle(Integer voucherId, Integer bookingId) {
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
