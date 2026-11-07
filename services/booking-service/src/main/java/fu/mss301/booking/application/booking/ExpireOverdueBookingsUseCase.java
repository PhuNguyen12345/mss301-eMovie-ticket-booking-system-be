package fu.mss301.booking.application.booking;

import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.application.payment.ReconcilePaymentUseCase;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.booking.BookingStatus;

import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.LocalDateTime;

/** Releases seats of bookings whose hold ran out without a payment. */
public class ExpireOverdueBookingsUseCase {
    private static final System.Logger log = System.getLogger(ExpireOverdueBookingsUseCase.class.getName());
    private final BookingRepository bookings;
    private final ReconcilePaymentUseCase reconciliation;
    private final Transactions transactions;
    private final Clock clock;

    public ExpireOverdueBookingsUseCase(BookingRepository bookings, ReconcilePaymentUseCase reconciliation,
                                        Transactions transactions, Clock clock) {
        this.bookings = bookings;
        this.reconciliation = reconciliation;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** @return number of bookings moved to EXPIRED */
    public int expireOverdue(int batchSize) {
        int expired = 0;
        var overdue = transactions.read(() -> bookings.findOverdueIds(LocalDateTime.now(clock), batchSize));
        for (Integer bookingId : overdue) {
            try {
                // A webhook may have been lost: if PayOS says the link was paid, settle instead of expiring.
                if (reconciliation.reconcileBooking(bookingId)) continue;
                if (transactions.write(() -> expireOne(bookingId))) expired++;
            } catch (RuntimeException exception) {
                log.log(Level.WARNING, () -> "Could not expire booking " + bookingId + ": " + exception.getMessage());
            }
        }
        return expired;
    }

    private boolean expireOne(Integer bookingId) {
        LocalDateTime now = LocalDateTime.now(clock);
        var booking = bookings.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.PENDING_PAYMENT || now.isBefore(booking.getExpiredAt())) {
            return false;
        }
        booking.expire(now);
        bookings.save(booking);
        return true;
    }
}
