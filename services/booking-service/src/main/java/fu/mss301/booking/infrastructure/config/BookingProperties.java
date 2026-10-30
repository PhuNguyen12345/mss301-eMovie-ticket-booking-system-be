package fu.mss301.booking.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "booking")
public record BookingProperties(String zone, Duration holdTtl, int maxSeats, Duration expiryScanInterval,
                                int expiryBatchSize) {}
