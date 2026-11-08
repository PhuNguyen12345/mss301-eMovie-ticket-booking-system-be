package fu.mss301.booking.presentation.controller;

import fu.mss301.booking.application.booking.ShowtimeCancellationUseCase;
import fu.mss301.booking.application.booking.ShowtimeCancellationView;
import fu.mss301.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

/**
 * Called by showtime-service after it cancels a showtime. The gateway does not route {@code /internal/**}, and the
 * call is checked against showtime-service, so a wrong or repeated call does no harm.
 */
@RestController
@RequestMapping("/internal/showtimes")
public class InternalShowtimeController {
    private final ShowtimeCancellationUseCase cancellation;

    public InternalShowtimeController(ShowtimeCancellationUseCase cancellation) {
        this.cancellation = cancellation;
    }

    @PostMapping("/{showtimeId}/cancellation")
    public ApiResponse<ShowtimeCancellationView> showtimeCancelled(@PathVariable("showtimeId") Long showtimeId) {
        return ApiResponse.success(cancellation.handle(showtimeId), "Bookings of the cancelled showtime handled");
    }
}
