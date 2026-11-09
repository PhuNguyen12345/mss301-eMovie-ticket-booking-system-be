package fu.mss301.booking.infrastructure.web.dto;

import fu.mss301.booking.application.booking.ComboLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReplaceCombosRequest(@NotNull @Size(max = 20) List<@NotNull @Valid Item> combos) {
    public record Item(@NotNull @Positive Integer comboId, @NotNull @Positive Integer quantity) {}

    public List<ComboLine> toLines() {
        return combos.stream().map(item -> new ComboLine(item.comboId(), item.quantity())).toList();
    }
}
