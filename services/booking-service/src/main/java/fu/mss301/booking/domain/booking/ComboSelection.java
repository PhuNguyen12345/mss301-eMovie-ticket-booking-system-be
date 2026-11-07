package fu.mss301.booking.domain.booking;

import java.math.BigDecimal;

/** A combo chosen for a booking, with the unit price it is sold at right now. */
public record ComboSelection(Integer comboId, String name, Integer quantity, BigDecimal price) {}
