package fu.mss301.booking.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "booking")
/**
 * @param valueVoucherTtl    how long a value voucher issued by the system can be used
 * @param checkInOpensBefore how long before a showtime starts its door opens for check-in
 * @param checkInClosesAfter how long after a showtime starts latecomers can still check in
 * @param showtimeCancelNotice cancelling a showtime closer than this to its start earns its customers an apology
 */
public record BookingProperties(String zone, Duration holdTtl, int maxSeats, Duration expiryScanInterval,
                                int expiryBatchSize, Duration valueVoucherTtl, Duration checkInOpensBefore,
                                Duration checkInClosesAfter, Duration showtimeCancelNotice) {}
