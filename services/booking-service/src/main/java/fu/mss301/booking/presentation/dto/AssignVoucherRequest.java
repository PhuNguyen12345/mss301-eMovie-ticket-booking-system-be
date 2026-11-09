package fu.mss301.booking.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AssignVoucherRequest(@NotNull @Positive Integer userId) {}
