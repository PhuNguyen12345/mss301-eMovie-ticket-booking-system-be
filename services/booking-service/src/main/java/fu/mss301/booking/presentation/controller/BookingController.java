package fu.mss301.booking.presentation.controller;

import fu.mss301.booking.application.booking.BookingPage;
import fu.mss301.booking.application.booking.BookingQueryService;
import fu.mss301.booking.application.booking.BookingSearch;
import fu.mss301.booking.application.booking.BookingView;
import fu.mss301.booking.application.booking.CancelBookingUseCase;
import fu.mss301.booking.application.booking.HoldSeatsUseCase;
import fu.mss301.booking.application.booking.ReplaceBookingCombosUseCase;
import fu.mss301.booking.application.booking.SeatMapQuery;
import fu.mss301.booking.application.booking.SeatMapView;
import fu.mss301.booking.application.payment.InitiatePaymentUseCase;
import fu.mss301.booking.application.payment.PaymentView;
import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.presentation.dto.HoldSeatsRequest;
import fu.mss301.booking.presentation.dto.ReplaceCombosRequest;
import fu.mss301.booking.presentation.security.AuthenticatedUser;
import fu.mss301.booking.presentation.security.CurrentUser;
import fu.mss301.common.dto.ApiResponse;
import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.common.exception.ApiException;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final SeatMapQuery seatMap;
    private final HoldSeatsUseCase holdSeats;
    private final BookingQueryService queries;
    private final CancelBookingUseCase cancelBooking;
    private final InitiatePaymentUseCase initiatePayment;
    private final ReplaceBookingCombosUseCase replaceCombos;

    public BookingController(SeatMapQuery seatMap, HoldSeatsUseCase holdSeats, BookingQueryService queries,
                             CancelBookingUseCase cancelBooking, InitiatePaymentUseCase initiatePayment,
                             ReplaceBookingCombosUseCase replaceCombos) {
        this.seatMap = seatMap;
        this.holdSeats = holdSeats;
        this.queries = queries;
        this.cancelBooking = cancelBooking;
        this.initiatePayment = initiatePayment;
        this.replaceCombos = replaceCombos;
    }

    @GetMapping
    public ApiResponse<PageResponseDTO<BookingView>> search(
            @CurrentUser AuthenticatedUser user,
            @RequestParam(name = "status", required = false) BookingStatus status,
            @RequestParam(name = "showtimeId", required = false) Long showtimeId,
            @RequestParam(name = "userId", required = false) Integer userId,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        if (!user.isAdmin()) throw new ApiException(HttpStatus.FORBIDDEN, "Access denied");
        var search = new BookingSearch(status, showtimeId, userId, code, from, to);
        return ApiResponse.success(toResponse(queries.search(search, page, size)), "Bookings retrieved successfully");
    }

    @GetMapping("/showtimes/{showtimeId}/seats")
    public ApiResponse<SeatMapView> getSeatMap(@PathVariable("showtimeId") Long showtimeId) {
        return ApiResponse.success(seatMap.seatMap(showtimeId), "Seat map retrieved successfully");
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BookingView>> holdSeats(@CurrentUser AuthenticatedUser user,
                                                              @Valid @RequestBody HoldSeatsRequest request) {
        var result = holdSeats.hold(user.id(), request.showtimeId(), request.seatIds());
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(result.id()).toUri();
        return ResponseEntity.created(location).body(new ApiResponse<>(201, "Seats held successfully", result));
    }

    @GetMapping("/me")
    public ApiResponse<PageResponseDTO<BookingView>> myBookings(@CurrentUser AuthenticatedUser user,
                                                                @RequestParam(name = "page", defaultValue = "0") int page,
                                                                @RequestParam(name = "size", defaultValue = "10") int size) {
        return ApiResponse.success(toResponse(queries.myBookings(user.id(), page, size)), "Bookings retrieved successfully");
    }

    @GetMapping("/{id}")
    public ApiResponse<BookingView> getBooking(@CurrentUser AuthenticatedUser user, @PathVariable("id") Integer id) {
        return ApiResponse.success(queries.getBooking(user.id(), user.isAdmin(), id), "Booking retrieved successfully");
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<BookingView> cancel(@CurrentUser AuthenticatedUser user, @PathVariable("id") Integer id) {
        return ApiResponse.success(cancelBooking.cancel(user.id(), id), "Booking cancelled successfully");
    }

    @PutMapping("/{id}/combos")
    public ApiResponse<BookingView> replaceCombos(@CurrentUser AuthenticatedUser user, @PathVariable("id") Integer id,
                                                  @Valid @RequestBody ReplaceCombosRequest request) {
        return ApiResponse.success(replaceCombos.replace(user.id(), id, request.toLines()), "Booking combos updated successfully");
    }

    @PostMapping("/{id}/payments")
    public ApiResponse<PaymentView> pay(@CurrentUser AuthenticatedUser user, @PathVariable("id") Integer id) {
        return ApiResponse.success(initiatePayment.initiate(user.id(), id), "Payment link created successfully");
    }

    private static PageResponseDTO<BookingView> toResponse(BookingPage<BookingView> result) {
        return new PageResponseDTO<>(result.content(), result.page(), result.size(), result.totalElements(),
            result.totalPages(), result.first(), result.last());
    }
}
