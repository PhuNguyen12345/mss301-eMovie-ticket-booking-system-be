package fu.mss301.cinema.infrastructure.web.controller;

import fu.mss301.cinema.application.port.in.*;
import fu.mss301.cinema.domain.model.*;
import fu.mss301.cinema.infrastructure.web.dto.CinemaRequests.*;
import fu.mss301.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class CinemaController {
    private final CinemaCatalog catalog;
    private final CinemaManagement management;
    public CinemaController(CinemaCatalog catalog, CinemaManagement management) {
        this.catalog = catalog; this.management = management;
    }

    @GetMapping("/api/cinemas")
    @Operation(tags = "Rạp chiếu", summary = "Danh sách rạp", description = "Trả về tất cả rạp, gồm cả rạp đang ngừng hoạt động. HTTP 200.")
    public ApiResponse<List<Cinema>> cinemas() { return ApiResponse.success(catalog.listCinemas(), "Cinemas retrieved"); }
    @GetMapping("/api/cinemas/{id}")
    @Operation(tags = "Rạp chiếu", summary = "Chi tiết rạp", description = "Trả về rạp theo ID. HTTP 404 nếu rạp không tồn tại.")
    public ApiResponse<Cinema> cinema(@Parameter(description = "ID rạp", example = "1") @PathVariable int id) { return ApiResponse.success(catalog.getCinema(id), "Cinema retrieved"); }
    @PostMapping("/api/cinemas")
    @Operation(tags = "Rạp chiếu", summary = "Tạo rạp", description = "Tạo rạp ở trạng thái ACTIVE. Yêu cầu quyền ADMIN qua API Gateway. HTTP 400 nếu dữ liệu sai; 409 nếu mã rạp đã tồn tại.")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo rạp thành công; Location trỏ tới rạp mới"))
    public ResponseEntity<ApiResponse<Cinema>> createCinema(@Valid @RequestBody CinemaBody body) {
        Cinema created = management.createCinema(body.code(), body.name(), body.location());
        return created("/api/cinemas/" + created.id(), created);
    }
    @PutMapping("/api/cinemas/{id}")
    @Operation(tags = "Rạp chiếu", summary = "Cập nhật rạp", description = "Cập nhật mã, tên và địa điểm; giữ nguyên trạng thái. Yêu cầu ADMIN. HTTP 400/404/409 khi dữ liệu sai, không tìm thấy hoặc trùng mã.")
    public ApiResponse<Cinema> updateCinema(@Parameter(description = "ID rạp", example = "1") @PathVariable int id, @Valid @RequestBody CinemaBody body) {
        return ApiResponse.success(management.updateCinema(id, body.code(), body.name(), body.location()), "Cinema updated");
    }
    @PatchMapping("/api/cinemas/{id}/status")
    @Operation(tags = "Rạp chiếu", summary = "Đổi trạng thái rạp", description = "Chỉ nhận ACTIVE hoặc INACTIVE. Khi rạp INACTIVE, danh sách ghế các phòng trả trạng thái không khả dụng. Yêu cầu ADMIN. HTTP 400/404.")
    public ApiResponse<Cinema> cinemaStatus(@Parameter(description = "ID rạp", example = "1") @PathVariable int id, @Valid @RequestBody StatusBody body) {
        return ApiResponse.success(management.setCinemaStatus(id, body.status()), "Cinema status updated");
    }

    @GetMapping("/api/cinemas/{cinemaId}/screens")
    @Operation(tags = "Phòng chiếu", summary = "Danh sách phòng của rạp", description = "Trả về các phòng thuộc rạp. HTTP 404 nếu rạp không tồn tại.")
    public ApiResponse<List<Screen>> screens(@Parameter(description = "ID rạp", example = "1") @PathVariable int cinemaId) { return ApiResponse.success(catalog.listScreens(cinemaId), "Screens retrieved"); }
    @GetMapping("/api/screens/{id}")
    @Operation(tags = "Phòng chiếu", summary = "Chi tiết phòng chiếu", description = "Trả về phòng theo ID, gồm cinemaId và status để showtime-service kiểm tra. HTTP 404 nếu không tồn tại.")
    public ApiResponse<Screen> screen(@Parameter(description = "ID phòng", example = "1") @PathVariable int id) { return ApiResponse.success(catalog.getScreen(id), "Screen retrieved"); }
    @PostMapping("/api/cinemas/{cinemaId}/screens")
    @Operation(tags = "Phòng chiếu", summary = "Tạo phòng chiếu", description = "Tạo phòng ACTIVE trong rạp. Mã phòng phải duy nhất trong cùng rạp. Yêu cầu ADMIN. HTTP 400/404/409.")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo phòng thành công; Location trỏ tới phòng mới"))
    public ResponseEntity<ApiResponse<Screen>> createScreen(@Parameter(description = "ID rạp", example = "1") @PathVariable int cinemaId, @Valid @RequestBody ScreenBody body) {
        Screen created = management.createScreen(cinemaId, body.code(), body.name());
        return created("/api/screens/" + created.id(), created);
    }
    @PutMapping("/api/screens/{id}")
    @Operation(tags = "Phòng chiếu", summary = "Cập nhật phòng chiếu", description = "Cập nhật mã và tên, giữ nguyên rạp và trạng thái. Yêu cầu ADMIN. HTTP 400/404/409.")
    public ApiResponse<Screen> updateScreen(@Parameter(description = "ID phòng", example = "1") @PathVariable int id, @Valid @RequestBody ScreenBody body) {
        return ApiResponse.success(management.updateScreen(id, body.code(), body.name()), "Screen updated");
    }
    @PatchMapping("/api/screens/{id}/status")
    @Operation(tags = "Phòng chiếu", summary = "Đổi trạng thái phòng", description = "Nhận ACTIVE, INACTIVE hoặc MAINTENANCE. Phòng ngừng hoạt động làm các ghế trong danh sách không khả dụng. Yêu cầu ADMIN. HTTP 400/404.")
    public ApiResponse<Screen> screenStatus(@Parameter(description = "ID phòng", example = "1") @PathVariable int id, @Valid @RequestBody StatusBody body) {
        return ApiResponse.success(management.setScreenStatus(id, body.status()), "Screen status updated");
    }

    @GetMapping("/api/seat-types")
    @Operation(tags = "Loại ghế", summary = "Danh sách loại ghế", description = "Trả về các loại ghế vật lý dùng để phân loại và định giá tại showtime-service. HTTP 200.")
    public ApiResponse<List<SeatType>> seatTypes() { return ApiResponse.success(catalog.listSeatTypes(), "Seat types retrieved"); }
    @GetMapping("/api/seat-types/{id}")
    @Operation(tags = "Loại ghế", summary = "Chi tiết loại ghế", description = "Trả về loại ghế theo ID. HTTP 404 nếu không tồn tại.")
    public ApiResponse<SeatType> seatType(@Parameter(description = "ID loại ghế", example = "1") @PathVariable int id) { return ApiResponse.success(catalog.getSeatType(id), "Seat type retrieved"); }
    @PostMapping("/api/seat-types")
    @Operation(tags = "Loại ghế", summary = "Tạo loại ghế", description = "Mã loại ghế là duy nhất toàn hệ thống. Yêu cầu ADMIN. HTTP 400 nếu dữ liệu sai; 409 nếu trùng mã.")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo loại ghế thành công"))
    public ResponseEntity<ApiResponse<SeatType>> createSeatType(@Valid @RequestBody SeatTypeBody body) {
        SeatType created = management.createSeatType(body.code(), body.name());
        return created("/api/seat-types/" + created.id(), created);
    }
    @PutMapping("/api/seat-types/{id}")
    @Operation(tags = "Loại ghế", summary = "Cập nhật loại ghế", description = "Cập nhật mã và tên; ID không đổi. Yêu cầu ADMIN. HTTP 400/404/409.")
    public ApiResponse<SeatType> updateSeatType(@Parameter(description = "ID loại ghế", example = "1") @PathVariable int id, @Valid @RequestBody SeatTypeBody body) {
        return ApiResponse.success(management.updateSeatType(id, body.code(), body.name()), "Seat type updated");
    }

    @GetMapping("/api/screens/{screenId}/seats")
    @Operation(tags = "Ghế", summary = "Sơ đồ ghế vật lý của phòng", description = "Trả về ghế theo hàng và số, gồm seatTypeId và status cho booking-service. Nếu rạp hoặc phòng không ACTIVE, các ghế được trả là INACTIVE. Trạng thái đã giữ/bán thuộc booking-service. HTTP 404 nếu phòng không tồn tại.")
    public ApiResponse<List<Seat>> seats(@Parameter(description = "ID phòng", example = "1") @PathVariable int screenId) { return ApiResponse.success(catalog.listSeats(screenId), "Seats retrieved"); }
    @GetMapping("/api/seats/{id}")
    @Operation(tags = "Ghế", summary = "Chi tiết ghế vật lý", description = "Trả về thông tin và trạng thái lưu của một ghế. HTTP 404 nếu ghế không tồn tại.")
    public ApiResponse<Seat> seat(@Parameter(description = "ID ghế", example = "1") @PathVariable int id) { return ApiResponse.success(catalog.getSeat(id), "Seat retrieved"); }
    @PostMapping("/api/screens/{screenId}/seats")
    @Operation(tags = "Ghế", summary = "Tạo ghế", description = "Tạo ghế ACTIVE. Cặp hàng và số ghế phải duy nhất trong phòng. Yêu cầu ADMIN. HTTP 400/404/409.")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo ghế thành công; Location trỏ tới ghế mới"))
    public ResponseEntity<ApiResponse<Seat>> createSeat(@Parameter(description = "ID phòng", example = "1") @PathVariable int screenId, @Valid @RequestBody SeatBody body) {
        Seat created = management.createSeat(screenId, body.rowCode(), body.seatNumber(), body.seatTypeId());
        return created("/api/seats/" + created.id(), created);
    }
    @PatchMapping("/api/seats/{id}/status")
    @Operation(tags = "Ghế", summary = "Đổi trạng thái ghế", description = "Nhận ACTIVE, INACTIVE hoặc MAINTENANCE. Ghế khác ACTIVE không thể được booking-service giữ. Yêu cầu ADMIN. HTTP 400/404.")
    public ApiResponse<Seat> seatStatus(@Parameter(description = "ID ghế", example = "1") @PathVariable int id, @Valid @RequestBody StatusBody body) {
        return ApiResponse.success(management.setSeatStatus(id, body.status()), "Seat status updated");
    }

    private <T> ResponseEntity<ApiResponse<T>> created(String path, T value) {
        URI uri = ServletUriComponentsBuilder.fromCurrentContextPath().path(path).build().toUri();
        return ResponseEntity.created(uri).body(new ApiResponse<>(201, "Created", value));
    }
}
