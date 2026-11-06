package fu.mss301.showtime.infrastructure.client;

import feign.FeignException;
import fu.mss301.common.dto.ApiResponse;
import fu.mss301.showtime.application.exception.UpstreamServiceException;
import java.util.function.Supplier;

final class UpstreamResponses {
    private UpstreamResponses() {}

    static <T> T get(Supplier<ApiResponse<T>> call, String resource) {
        try {
            var response = call.get();
            if (response == null || response.getStatus() != 200 || response.getData() == null) {
                throw new UpstreamServiceException(resource + " service returned an invalid response");
            }
            return response.getData();
        } catch (FeignException exception) {
            if (exception.status() == 404) throw new IllegalArgumentException(resource + " does not exist");
            throw new UpstreamServiceException(resource + " service is unavailable");
        }
    }
}
