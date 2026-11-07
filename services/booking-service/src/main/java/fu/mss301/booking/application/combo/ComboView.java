package fu.mss301.booking.application.combo;

import fu.mss301.booking.domain.combo.Combo;
import fu.mss301.booking.domain.combo.ComboStatus;

import java.math.BigDecimal;

public record ComboView(Integer id, String name, String description, BigDecimal price, ComboStatus status) {
    public static ComboView of(Combo combo) {
        return new ComboView(combo.id(), combo.name(), combo.description(), combo.price(), combo.status());
    }
}
