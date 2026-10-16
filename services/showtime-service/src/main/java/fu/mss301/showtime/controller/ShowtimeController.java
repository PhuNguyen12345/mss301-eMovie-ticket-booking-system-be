package fu.mss301.showtime.controller;

import fu.mss301.common.dto.ApiResponse;
import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.showtime.dto.ShowtimeRequestDTO;
import fu.mss301.showtime.dto.ShowtimeResponseDTO;
import fu.mss301.showtime.enums.ShowtimeStatus;
import fu.mss301.showtime.service.ShowtimeService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/showtimes")
public class ShowtimeController {
    private final ShowtimeService service;

    public ShowtimeController(ShowtimeService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponseDTO<ShowtimeResponseDTO>> getShowtimes(
            @RequestParam(name = "movieId", required = false) Integer movieId,
            @RequestParam(name = "cinemaId", required = false) Integer cinemaId,
            @RequestParam(name = "screenId", required = false) Integer screenId,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "location", required = false) String location,
            @RequestParam(name = "status", required = false) ShowtimeStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return ApiResponse.success(service.getShowtimes(movieId, cinemaId, screenId, date, location, status, page, size),
            "Showtimes retrieved successfully");
    }

    @GetMapping("/{id}")
    public ApiResponse<ShowtimeResponseDTO> getShowtime(@PathVariable("id") Long id) {
        return ApiResponse.success(service.getShowtimeById(id), "Showtime retrieved successfully");
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ShowtimeResponseDTO>> createShowtime(@RequestBody ShowtimeRequestDTO request) {
        var result = service.createShowtime(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(result.id()).toUri();
        return ResponseEntity.created(location).body(new ApiResponse<>(201, "Showtime created successfully", result));
    }

    @PutMapping("/{id}")
    public ApiResponse<ShowtimeResponseDTO> updateShowtime(@PathVariable("id") Long id,
            @RequestBody ShowtimeRequestDTO request) {
        return ApiResponse.success(service.updateShowtime(id, request), "Showtime updated successfully");
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<ShowtimeResponseDTO> cancelShowtime(@PathVariable("id") Long id) {
        return ApiResponse.success(service.cancelShowtime(id), "Showtime cancelled successfully");
    }
}
