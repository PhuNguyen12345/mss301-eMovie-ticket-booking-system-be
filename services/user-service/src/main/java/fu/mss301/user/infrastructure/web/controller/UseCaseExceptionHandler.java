package fu.mss301.user.infrastructure.web.controller;

import fu.mss301.common.dto.ApiResponse;
import fu.mss301.user.application.usecase.UseCaseException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotSupportedException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UseCaseExceptionHandler {
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<String>> unsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity.status(415).body(ApiResponse.error(415, "Unsupported content type"));
    }

    @ExceptionHandler(UseCaseException.class)
    public ResponseEntity<ApiResponse<String>> handle(UseCaseException exception) {
        HttpHeaders headers = new HttpHeaders();
        if (exception.retryAfterSeconds() != null) {
            headers.add(HttpHeaders.RETRY_AFTER, exception.retryAfterSeconds().toString());
        }
        int status = exception.getStatus().value();
        return ResponseEntity.status(status).headers(headers)
                .body(ApiResponse.error(status, exception.getMessage()));
    }
}
