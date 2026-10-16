package fu.mss301.gateway.exception;

import fu.mss301.common.dto.ApiResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

@Component
public class GatewayErrorWriter {
    private final ObjectMapper mapper;

    public GatewayErrorWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, int status, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatusCode.valueOf(status));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = mapper.writeValueAsBytes(ApiResponse.error(status, message));
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
