package fu.mss301.booking.infrastructure.client;

import feign.FeignException;
import fu.mss301.booking.application.exception.UpstreamServiceException;
import fu.mss301.common.dto.ApiResponse;
import fu.mss301.common.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.function.Supplier;

/** Unwraps an {@link ApiResponse} from another service and maps transport failures. */
final class Upstream {
    private Upstream() {
    }

    static <T> T call(Supplier<ApiResponse<T>> call, String resource) {
        try {
            ApiResponse<T> response = call.get();
            if (response == null || response.getStatus() != 200 || response.getData() == null) {
                throw new UpstreamServiceException(resource + " service returned an invalid response");
            }
            return response.getData();
        } catch (FeignException exception) {
            if (exception.status() == 404) throw new ApiException(HttpStatus.NOT_FOUND, resource + " not found");
            throw new UpstreamServiceException(resource + " service is unavailable");
        }
    }
}
