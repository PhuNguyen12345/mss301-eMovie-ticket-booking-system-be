package fu.mss301.booking.infrastructure.web.exception;

import fu.mss301.booking.application.exception.BookingNotFoundException;
import fu.mss301.booking.application.exception.ComboNotFoundException;
import fu.mss301.booking.application.exception.ComboUnavailableException;
import fu.mss301.booking.application.exception.InvalidWebhookException;
import fu.mss301.booking.application.exception.PaymentGatewayException;
import fu.mss301.booking.application.exception.RefundNotFoundException;
import fu.mss301.booking.application.exception.SeatUnavailableException;
import fu.mss301.booking.application.booking.CheckInView;
import fu.mss301.booking.application.exception.TicketAlreadyUsedException;
import fu.mss301.booking.application.exception.UpstreamServiceException;
import fu.mss301.booking.application.exception.VoucherNotFoundException;
import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.shared.ErrorCode;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;
import fu.mss301.booking.domain.voucher.VoucherCodeTakenException;
import fu.mss301.common.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps domain/application exceptions to HTTP. Runs before the shared GlobalExceptionHandler, which still
 * handles ApiException, bean validation and the 500 fallback.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BookingExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(BookingExceptionHandler.class);

    @ExceptionHandler({BookingNotFoundException.class, RefundNotFoundException.class, ComboNotFoundException.class,
        VoucherNotFoundException.class})
    ResponseEntity<ApiResponse<Object>> notFound(RuntimeException exception) {
        ErrorCode code = exception instanceof BookingNotFoundException ? ErrorCode.BOOKING_NOT_FOUND
            : exception instanceof ComboNotFoundException ? ErrorCode.COMBO_NOT_FOUND
            : exception instanceof VoucherNotFoundException ? ErrorCode.VOUCHER_NOT_FOUND
            : ErrorCode.NOT_FOUND;
        return error(HttpStatus.NOT_FOUND, code, exception.getMessage(), null);
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    ResponseEntity<ApiResponse<Object>> conflict(InvalidStateTransitionException exception) {
        return coded(HttpStatus.CONFLICT, exception, ErrorCode.CONFLICT);
    }

    @ExceptionHandler(SeatUnavailableException.class)
    ResponseEntity<ApiResponse<Object>> seatUnavailable(SeatUnavailableException exception) {
        return error(HttpStatus.CONFLICT, exception.code(), exception.getMessage(), null);
    }

    @ExceptionHandler(ComboUnavailableException.class)
    ResponseEntity<ApiResponse<Object>> comboUnavailable(ComboUnavailableException exception) {
        return error(HttpStatus.CONFLICT, ErrorCode.COMBO_UNAVAILABLE, exception.getMessage(), null);
    }

    @ExceptionHandler(VoucherCodeTakenException.class)
    ResponseEntity<ApiResponse<Object>> voucherCodeTaken(VoucherCodeTakenException exception) {
        return error(HttpStatus.CONFLICT, ErrorCode.CONFLICT, exception.getMessage(), null);
    }

    /** Carries the earlier scans, so door staff see when the ticket was used. */
    @ExceptionHandler(TicketAlreadyUsedException.class)
    ResponseEntity<ApiResponse<CheckInView>> alreadyUsed(TicketAlreadyUsedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(HttpStatus.CONFLICT.value(),
            ErrorCode.TICKET_ALREADY_USED.name(), exception.getMessage(), exception.booking()));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ApiResponse<Object>> concurrentUpdate(RuntimeException exception) {
        return error(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_UPDATE,
            "The booking was changed by another request, please retry", null);
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiResponse<Object>> domain(DomainException exception) {
        return coded(HttpStatus.BAD_REQUEST, exception, ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler({InvalidWebhookException.class, IllegalArgumentException.class})
    ResponseEntity<ApiResponse<Object>> badRequest(RuntimeException exception) {
        return error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, exception.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiResponse<Object>> typeMismatch(MethodArgumentTypeMismatchException exception) {
        return error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST,
            "Invalid value for parameter '" + exception.getName() + "'", null);
    }

    /** Same message as the shared handler, plus the generic code. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Object>> invalidBody(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream().findFirst()
            .map(error -> error.getField() + ": " + error.getDefaultMessage()).orElse("Invalid request");
        return error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, message, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Object>> unreadableBody(HttpMessageNotReadableException exception) {
        return error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, "Invalid request body", null);
    }

    @ExceptionHandler(UpstreamServiceException.class)
    ResponseEntity<ApiResponse<Object>> upstream(UpstreamServiceException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, null, exception.getMessage(), null);
    }

    @ExceptionHandler(PaymentGatewayException.class)
    ResponseEntity<ApiResponse<Object>> paymentGateway(PaymentGatewayException exception) {
        log.warn("Payment gateway error: {}", exception.getMessage(), exception.getCause());
        return error(HttpStatus.BAD_GATEWAY, null, "Payment provider is unavailable, please try again", null);
    }

    /** The exception's own code and details, or the generic code of the status. */
    private static ResponseEntity<ApiResponse<Object>> coded(HttpStatus status, DomainException exception,
                                                             ErrorCode generic) {
        ErrorCode code = exception.code() == null ? generic : exception.code();
        Object details = exception.details().isEmpty() ? null : exception.details();
        return error(status, code, exception.getMessage(), details);
    }

    private static ResponseEntity<ApiResponse<Object>> error(HttpStatus status, ErrorCode code, String message,
                                                             Object data) {
        return ResponseEntity.status(status)
            .body(ApiResponse.error(status.value(), code == null ? null : code.name(), message, data));
    }
}
