package fu.mss301.booking.application.booking;

import java.time.Duration;

/** Configurable business limits for seat holds. */
public record HoldPolicy(Duration holdTtl, int maxSeats) {}
