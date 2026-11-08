package fu.mss301.booking.infrastructure.scheduling;

import fu.mss301.booking.application.booking.ExpireOverdueBookingsUseCase;
import fu.mss301.booking.application.payment.ReconcilePaymentUseCase;
import fu.mss301.booking.application.voucher.VoucherReservations;
import fu.mss301.booking.infrastructure.config.BookingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Releases expired seat holds, re-drives payments whose booking update was missed and settles vouchers a booking
 * has held for too long.
 */
@Component
@ConditionalOnProperty(name = "booking.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class BookingMaintenanceScheduler {
    private static final Logger log = LoggerFactory.getLogger(BookingMaintenanceScheduler.class);
    private final ExpireOverdueBookingsUseCase expireOverdue;
    private final ReconcilePaymentUseCase reconcile;
    private final VoucherReservations vouchers;
    private final BookingProperties properties;

    BookingMaintenanceScheduler(ExpireOverdueBookingsUseCase expireOverdue, ReconcilePaymentUseCase reconcile,
                                VoucherReservations vouchers, BookingProperties properties) {
        this.expireOverdue = expireOverdue;
        this.reconcile = reconcile;
        this.vouchers = vouchers;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${booking.expiry-scan-interval:PT30S}", initialDelayString = "PT10S")
    void run() {
        try {
            int expired = expireOverdue.expireOverdue(properties.expiryBatchSize());
            if (expired > 0) log.info("Expired {} unpaid bookings", expired);
            reconcile.settleUnsettled(properties.expiryBatchSize());
            vouchers.settleStale(properties.expiryBatchSize());
        } catch (RuntimeException exception) {
            log.error("Booking maintenance run failed", exception);
        }
    }
}
