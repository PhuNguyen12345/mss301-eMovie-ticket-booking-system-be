package fu.mss301.booking.presentation.controller;

import fu.mss301.booking.application.booking.ApplyVoucherUseCase;
import fu.mss301.booking.application.booking.BookingPage;
import fu.mss301.booking.application.booking.BookingQueryService;
import fu.mss301.booking.application.booking.BookingSearch;
import fu.mss301.booking.application.booking.BookingView;
import fu.mss301.booking.application.booking.CancelBookingUseCase;
import fu.mss301.booking.application.booking.CheckInUseCase;
import fu.mss301.booking.application.booking.CheckInView;
import fu.mss301.booking.application.booking.ShowtimeCancellationUseCase;
import fu.mss301.booking.application.booking.ShowtimeCancellationView;
import fu.mss301.booking.application.booking.TicketQrUseCase;
import fu.mss301.booking.application.booking.HoldSeatsUseCase;
import fu.mss301.booking.application.booking.ReplaceBookingCombosUseCase;
import fu.mss301.booking.application.booking.SeatMapQuery;
import fu.mss301.booking.application.booking.SeatMapView;
import fu.mss301.booking.application.payment.InitiatePaymentUseCase;
import fu.mss301.booking.application.payment.PaymentView;
import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.presentation.dto.ApplyVoucherRequest;
import fu.mss301.booking.presentation.dto.CheckInRequest;
import fu.mss301.booking.presentation.dto.HoldSeatsRequest;
import fu.mss301.booking.presentation.dto.ReplaceCombosRequest;
import fu.mss301.booking.presentation.security.AuthenticatedUser;
import fu.mss301.booking.presentation.security.CurrentUser;
import fu.mss301.common.dto.ApiResponse;
import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.common.exception.ApiException;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    private final ApplyVoucherUseCase vouchers;
    private final CheckInUseCase checkIn;
    private final TicketQrUseCase ticketQr;
    private final ShowtimeCancellationUseCase showtimeCancellation;

    public BookingController(SeatMapQuery seatMap, HoldSeatsUseCase holdSeats, BookingQueryService queries,
                             CancelBookingUseCase cancelBooking, InitiatePaymentUseCase initiatePayment,
                             ReplaceBookingCombosUseCase replaceCombos, ApplyVoucherUseCase vouchers,
                             CheckInUseCase checkIn, TicketQrUseCase ticketQr,
                             ShowtimeCancellationUseCase showtimeCancellation) {
        this.seatMap = seatMap;
        this.holdSeats = holdSeats;
        this.queries = queries;
        this.cancelBooking = cancelBooking;
        this.initiatePayment = initiatePayment;
        this.replaceCombos = replaceCombos;
        this.vouchers = vouchers;
        this.checkIn = checkIn;
        this.ticketQr = ticketQr;
        this.showtimeCancellation = showtimeCancellation;
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

    /** Runs again what showtime-service triggers after cancelling a showtime, for when that call failed. */
    @PostMapping("/showtimes/{showtimeId}/cancellation")
    public ApiResponse<ShowtimeCancellationView> showtimeCancelled(@CurrentUser AuthenticatedUser user,
                                                                   @PathVariable("showtimeId") Long showtimeId) {
        if (!user.isAdmin()) throw new ApiException(HttpStatus.FORBIDDEN, "Access denied");
        return ApiResponse.success(showtimeCancellation.handle(showtimeId), "Bookings of the cancelled showtime handled");
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

    /** Door staff scan the customer's QR code, which holds the booking code. */
    @PostMapping("/check-in")
    public ApiResponse<CheckInView> checkIn(@CurrentUser AuthenticatedUser user,
                                            @Valid @RequestBody CheckInRequest request) {
        if (!user.isAdmin()) throw new ApiException(HttpStatus.FORBIDDEN, "Access denied");
        return ApiResponse.success(checkIn.checkIn(user.id(), request.bookingCode(), request.quantity()),
            "Checked in successfully");
    }

    @GetMapping(value = "/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> ticketQr(@CurrentUser AuthenticatedUser user, @PathVariable("id") Integer id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
            .cacheControl(CacheControl.noStore()).body(ticketQr.png(user.id(), user.isAdmin(), id));
    }

    @PostMapping("/{id}/vouchers")
    public ApiResponse<BookingView> applyVoucher(@CurrentUser AuthenticatedUser user, @PathVariable("id") Integer id,
                                                 @Valid @RequestBody ApplyVoucherRequest request) {
        return ApiResponse.success(vouchers.apply(user.id(), id, request.code(), request.seatId()),
            "Voucher applied successfully");
    }

    @DeleteMapping("/{id}/vouchers/{voucherId}")
    public ApiResponse<BookingView> removeVoucher(@CurrentUser AuthenticatedUser user, @PathVariable("id") Integer id,
                                                  @PathVariable("voucherId") Integer voucherId) {
        return ApiResponse.success(vouchers.remove(user.id(), id, voucherId), "Voucher removed successfully");
    }

    @PostMapping("/{id}/payments")
    public ApiResponse<PaymentView> pay(@CurrentUser AuthenticatedUser user, @PathVariable("id") Integer id) {
        var payment = initiatePayment.initiate(user.id(), id);
        String message = PaymentView.NOT_REQUIRED.equals(payment.status())
            ? "Booking confirmed, vouchers cover the whole amount" : "Payment link created successfully";
        return ApiResponse.success(payment, message);
    }

    private static PageResponseDTO<BookingView> toResponse(BookingPage<BookingView> result) {
        return new PageResponseDTO<>(result.content(), result.page(), result.size(), result.totalElements(),
            result.totalPages(), result.first(), result.last());
    }
}
