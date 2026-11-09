package fu.mss301.showtime.infrastructure.client;

import fu.mss301.showtime.application.port.BookingNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Calls booking-service in the background, so cancelling a showtime does not wait for every booking to be
 * handled. A failure is only logged; {@code POST /api/bookings/showtimes/{id}/cancellation} re-runs it.
 */
@Component
public class FeignBookingNotifier implements BookingNotifier {
    private static final Logger log = LoggerFactory.getLogger(FeignBookingNotifier.class);

    private final BookingClient client;
    private final Executor executor;

    @Autowired
    public FeignBookingNotifier(BookingClient client) {
        this(client, Executors.newVirtualThreadPerTaskExecutor());
    }

    FeignBookingNotifier(BookingClient client, Executor executor) {
        this.client = client;
        this.executor = executor;
    }

    @Override
    public void showtimeCancelled(Long showtimeId) {
        try {
            executor.execute(() -> notify(showtimeId));
        } catch (RuntimeException exception) {
            log.warn("Could not schedule booking handling of cancelled showtime {}", showtimeId, exception);
        }
    }

    private void notify(Long showtimeId) {
        try {
            var response = client.showtimeCancelled(showtimeId);
            var result = response == null ? null : response.getData();
            if (result == null) {
                log.warn("Booking service gave no result for cancelled showtime {}", showtimeId);
            } else if (result.failed() > 0) {
                log.warn("Booking service failed {} booking(s) of cancelled showtime {}; re-run it from the admin API",
                    result.failed(), showtimeId);
            } else {
                log.info("Booking service handled cancelled showtime {}: {} pending, {} paid",
                    showtimeId, result.cancelledPending(), result.cancelledPaid());
            }
        } catch (RuntimeException exception) {
            log.warn("Could not notify booking service of cancelled showtime {}; re-run it from the admin API",
                showtimeId, exception);
        }
    }
}
