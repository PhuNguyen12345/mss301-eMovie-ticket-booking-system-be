package fu.mss301.booking.infrastructure.web.dto;

import fu.mss301.booking.application.combo.ComboCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ComboRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 255) String description,
    @NotNull @Positive BigDecimal price
) {
    public ComboCommand toCommand() {
        return new ComboCommand(name, description, price);
    }
}
