package fu.mss301.booking.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClaimVoucherRequest(@NotBlank @Size(max = 50) String code) {}
