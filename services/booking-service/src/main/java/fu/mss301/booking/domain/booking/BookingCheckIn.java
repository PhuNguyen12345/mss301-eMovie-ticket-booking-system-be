package fu.mss301.booking.domain.booking;

import java.time.LocalDateTime;

/** Child entity of {@link Booking}: one scan at the cinema door and how many people it let in. */
public final class BookingCheckIn {
    private final Integer id;
    private final int quantity;
    private final Integer checkedInBy;
    private final LocalDateTime checkedInAt;

    private BookingCheckIn(Integer id, int quantity, Integer checkedInBy, LocalDateTime checkedInAt) {
        this.id = id;
        this.quantity = quantity;
        this.checkedInBy = checkedInBy;
        this.checkedInAt = checkedInAt;
    }

    static BookingCheckIn record(int quantity, Integer staffId, LocalDateTime now) {
        return new BookingCheckIn(null, quantity, staffId, now);
    }

    /** Rebuilds a stored check-in; performs no business validation. */
    public static BookingCheckIn restore(Integer id, int quantity, Integer checkedInBy, LocalDateTime checkedInAt) {
        return new BookingCheckIn(id, quantity, checkedInBy, checkedInAt);
    }

    public Integer id() { return id; }
    public int quantity() { return quantity; }
    public Integer checkedInBy() { return checkedInBy; }
    public LocalDateTime checkedInAt() { return checkedInAt; }
}
