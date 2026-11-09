package fu.mss301.booking.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** @param quantity how many people go in now; left out lets in everyone not yet in */
public record CheckInRequest(@NotBlank @Size(max = 50) String bookingCode, @Positive Integer quantity) {}
