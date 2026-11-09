package fu.mss301.booking.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** @param seatId required for a ticket exchange voucher, which pays for that one seat; left out otherwise */
public record ApplyVoucherRequest(@NotBlank @Size(max = 50) String code, @Positive Integer seatId) {}
