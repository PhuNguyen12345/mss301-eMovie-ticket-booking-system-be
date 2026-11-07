package fu.mss301.booking.application.combo;

import java.math.BigDecimal;

public record ComboCommand(String name, String description, BigDecimal price) {}
