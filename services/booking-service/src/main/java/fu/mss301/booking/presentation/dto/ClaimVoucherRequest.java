package fu.mss301.booking.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClaimVoucherRequest(@NotBlank @Size(max = 50) String code) {}
