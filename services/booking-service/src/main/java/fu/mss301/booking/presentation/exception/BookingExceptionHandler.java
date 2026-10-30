package fu.mss301.booking.presentation.exception;

import fu.mss301.booking.application.exception.InvalidWebhookException;
import fu.mss301.booking.application.exception.PaymentGatewayException;
import fu.mss301.booking.application.exception.SeatUnavailableException;
import fu.mss301.booking.application.exception.UpstreamServiceException;
import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;
import fu.mss301.common.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @ExceptionHandler({InvalidStateTransitionException.class, SeatUnavailableException.class})
    ResponseEntity<ApiResponse<String>> conflict(RuntimeException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ApiResponse<String>> concurrentUpdate(RuntimeException exception) {
        return error(HttpStatus.CONFLICT, "The booking was changed by another request, please retry");
    }

    @ExceptionHandler({DomainException.class, InvalidWebhookException.class, IllegalArgumentException.class})
    ResponseEntity<ApiResponse<String>> badRequest(RuntimeException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiResponse<String>> typeMismatch(MethodArgumentTypeMismatchException exception) {
        return error(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + exception.getName() + "'");
    }

    @ExceptionHandler(UpstreamServiceException.class)
    ResponseEntity<ApiResponse<String>> upstream(UpstreamServiceException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(PaymentGatewayException.class)
    ResponseEntity<ApiResponse<String>> paymentGateway(PaymentGatewayException exception) {
        log.warn("Payment gateway error: {}", exception.getMessage(), exception.getCause());
        return error(HttpStatus.BAD_GATEWAY, "Payment provider is unavailable, please try again");
    }

    private static ResponseEntity<ApiResponse<String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(status.value(), message));
    }
}
