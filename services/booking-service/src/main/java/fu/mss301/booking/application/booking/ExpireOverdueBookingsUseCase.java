package fu.mss301.booking.application.booking;

import fu.mss301.booking.application.TransactionRunner;
import fu.mss301.booking.application.payment.ReconcilePaymentUseCase;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.booking.BookingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/** Releases seats of bookings whose hold ran out without a payment. */
@Service
public class ExpireOverdueBookingsUseCase {
    private static final Logger log = LoggerFactory.getLogger(ExpireOverdueBookingsUseCase.class);
    private final BookingRepository bookings;
    private final ReconcilePaymentUseCase reconciliation;
    private final TransactionRunner tx;
    private final Clock clock;

    public ExpireOverdueBookingsUseCase(BookingRepository bookings, ReconcilePaymentUseCase reconciliation,
                                        TransactionRunner tx, Clock clock) {
        this.bookings = bookings;
        this.reconciliation = reconciliation;
        this.tx = tx;
        this.clock = clock;
    }

    /** @return number of bookings moved to EXPIRED */
    public int expireOverdue(int batchSize) {
        int expired = 0;
        for (Integer bookingId : bookings.findOverdueIds(LocalDateTime.now(clock), batchSize)) {
            try {
                // A webhook may have been lost: if PayOS says the link was paid, settle instead of expiring.
                if (reconciliation.reconcileBooking(bookingId)) continue;
                if (tx.call(() -> expireOne(bookingId))) expired++;
            } catch (RuntimeException exception) {
                log.warn("Could not expire booking {}: {}", bookingId, exception.getMessage());
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
