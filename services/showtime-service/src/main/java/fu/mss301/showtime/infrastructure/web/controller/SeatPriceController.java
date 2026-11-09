package fu.mss301.showtime.infrastructure.web.controller;

import fu.mss301.common.dto.ApiResponse;
import fu.mss301.showtime.application.dto.SeatPriceCommand;
import fu.mss301.showtime.application.dto.SeatPriceView;
import fu.mss301.showtime.application.usecase.SeatPriceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/showtimes/{id}/seat-prices")
public class SeatPriceController {
    private final SeatPriceService service;

    public SeatPriceController(SeatPriceService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<SeatPriceView>> getSeatPrices(@PathVariable("id") Long id) {
        return ApiResponse.success(service.getSeatPrices(id), "Seat prices retrieved successfully");
    }

    @PutMapping
    public ApiResponse<List<SeatPriceView>> replaceSeatPrices(@PathVariable("id") Long id,
            @RequestBody List<SeatPriceCommand> requests) {
        return ApiResponse.success(service.replaceSeatPrices(id, requests), "Seat prices updated successfully");
    }
}
