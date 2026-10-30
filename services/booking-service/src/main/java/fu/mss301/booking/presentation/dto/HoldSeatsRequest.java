package fu.mss301.booking.presentation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record HoldSeatsRequest(
    @NotNull @Positive Long showtimeId,
    @NotEmpty @Size(max = 20) List<@NotNull @Positive Integer> seatIds
) {}
