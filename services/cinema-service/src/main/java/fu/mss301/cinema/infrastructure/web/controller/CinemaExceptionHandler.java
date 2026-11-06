package fu.mss301.cinema.infrastructure.web.controller;

import fu.mss301.cinema.application.usecase.CinemaNotFoundException;
import fu.mss301.cinema.domain.exception.CinemaDomainException;
import fu.mss301.common.dto.ApiResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CinemaExceptionHandler {
    @ExceptionHandler(CinemaNotFoundException.class)
    public ResponseEntity<ApiResponse<String>> missing(CinemaNotFoundException ex) {
        return ResponseEntity.status(404).body(ApiResponse.error(404, ex.getMessage()));
    }
    @ExceptionHandler(CinemaDomainException.class)
    public ResponseEntity<ApiResponse<String>> invalid(CinemaDomainException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, ex.getMessage()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<String>> conflict(DataIntegrityViolationException ex) {
        return ResponseEntity.status(409).body(ApiResponse.error(409, "Duplicate code or seat position"));
    }
}
