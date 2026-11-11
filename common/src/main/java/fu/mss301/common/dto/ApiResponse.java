package fu.mss301.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

public class ApiResponse<T> {
    private int status;
    private String message;
    private T data;
    /** Stable, machine-readable reason of an error, for clients to show their own text; absent on success. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String code;
    private LocalDateTime timestamp;

    public ApiResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public ApiResponse(int status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(200, message, data);
    }

    public static <T> ApiResponse<T> error(int status, String message) {
        return new ApiResponse<>(status, message, null);
    }

    /** An error with a stable code and optional details, e.g. the opening hours of check-in. */
    public static <T> ApiResponse<T> error(int status, String code, String message, T data) {
        ApiResponse<T> response = new ApiResponse<>(status, message, data);
        response.code = code;
        return response;
    }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
