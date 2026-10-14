package fu.mss301.movie.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import fu.mss301.common.dto.*;
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice 
public class MovieExceptionHandler {
    
    @ExceptionHandler (MovieNotFoundException.class) 
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
        MovieNotFoundException exception
    ) {
        return ResponseEntity.status(404).body(
            ApiResponse.<Void>error(404, exception.getMessage())
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(
        IllegalArgumentException exception
    ) {
        return ResponseEntity.badRequest().body(
            ApiResponse.<Void>error(400, exception.getMessage())
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidParameter(
        MethodArgumentTypeMismatchException exception
    ) {
        return ResponseEntity.badRequest().body(
            ApiResponse.<Void>error(400, "Invalid value for parameter: " + exception.getName())
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidBody(
        HttpMessageNotReadableException exception
    ) {
        return ResponseEntity.badRequest().body(
            ApiResponse.<Void>error(400, "Request body is missing or invalid; check JSON, dates and status values")
        );
    }

    @ExceptionHandler(IOException.class)
public ResponseEntity<ApiResponse<Void>> handleUploadFailure(
    IOException exception
) {
    return ResponseEntity.status(502).body(
        ApiResponse.<Void>error(502, "Image upload failed")
    );
}

@ExceptionHandler(MaxUploadSizeExceededException.class)
public ResponseEntity<ApiResponse<Void>> handleOversizedFile(
    MaxUploadSizeExceededException exception
) {
    return ResponseEntity.status(413).body(
        ApiResponse.<Void>error(413, "Image must not exceed 5 MB")
    );
}

@ExceptionHandler(MissingServletRequestParameterException.class)
public ResponseEntity<ApiResponse<Void>> handleMissingFile(
    MissingServletRequestParameterException exception
) {
    return ResponseEntity.badRequest().body(
        ApiResponse.<Void>error(400, "Required request parameter is missing")
    );
}
    
}
