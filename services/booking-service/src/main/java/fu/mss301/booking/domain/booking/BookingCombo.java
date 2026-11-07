package fu.mss301.booking.domain.booking;

import java.math.BigDecimal;

/**
 * Child entity of {@link Booking}: one combo line. {@code price} is the unit price at the time of purchase, so
 * later menu changes do not affect the booking. {@code name} is for display and is not stored on the line.
 */
public final class BookingCombo {
    private final Integer id;
    private final Integer comboId;
    private final String name;
    private final Integer quantity;
    private final BigDecimal price;

    private BookingCombo(Integer id, Integer comboId, String name, Integer quantity, BigDecimal price) {
        this.id = id;
        this.comboId = comboId;
        this.name = name;
        this.quantity = quantity;
        this.price = price;
    }

    static BookingCombo add(ComboSelection selection) {
        return new BookingCombo(null, selection.comboId(), selection.name(), selection.quantity(), selection.price());
    }

    /** Rebuilds a stored line; performs no business validation. */
    public static BookingCombo restore(Integer id, Integer comboId, String name, Integer quantity, BigDecimal price) {
        return new BookingCombo(id, comboId, name, quantity, price);
    }

    BookingCombo replacedBy(ComboSelection selection) {
        return new BookingCombo(id, comboId, selection.name(), selection.quantity(), selection.price());
    }

    BigDecimal lineTotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    public Integer id() { return id; }
    public Integer comboId() { return comboId; }
    public String name() { return name; }
    public Integer quantity() { return quantity; }
    public BigDecimal price() { return price; }
}
