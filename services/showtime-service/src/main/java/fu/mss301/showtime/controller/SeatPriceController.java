package fu.mss301.showtime.controller;

import fu.mss301.common.dto.ApiResponse;
import fu.mss301.showtime.dto.SeatPriceRequestDTO;
import fu.mss301.showtime.dto.SeatPriceResponseDTO;
import fu.mss301.showtime.service.SeatPriceService;
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
    public ApiResponse<List<SeatPriceResponseDTO>> getSeatPrices(@PathVariable("id") Long id) {
        return ApiResponse.success(service.getSeatPrices(id), "Seat prices retrieved successfully");
    }

    @PutMapping
    public ApiResponse<List<SeatPriceResponseDTO>> replaceSeatPrices(@PathVariable("id") Long id,
            @RequestBody List<SeatPriceRequestDTO> requests) {
        return ApiResponse.success(service.replaceSeatPrices(id, requests), "Seat prices updated successfully");
    }
}
