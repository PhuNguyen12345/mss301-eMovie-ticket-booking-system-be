package fu.mss301.booking.domain.booking;

import fu.mss301.booking.domain.shared.DomainException;

import java.time.Duration;
import java.time.LocalDateTime;

/** When the door is open for a showtime: from {@code opensBefore} its start until {@code closesAfter} it. */
public record CheckInWindow(Duration opensBefore, Duration closesAfter) {
    public CheckInWindow {
        if (opensBefore == null || opensBefore.isNegative() || closesAfter == null || closesAfter.isNegative()) {
            throw new DomainException("Check-in window must not be negative");
        }
    }

    public LocalDateTime opensAt(LocalDateTime start) {
        return start.minus(opensBefore);
    }

    public LocalDateTime closesAt(LocalDateTime start) {
        return start.plus(closesAfter);
    }

    /** Both ends are included. */
    public boolean isOpenAt(LocalDateTime start, LocalDateTime now) {
        return !now.isBefore(opensAt(start)) && !now.isAfter(closesAt(start));
    }
}
