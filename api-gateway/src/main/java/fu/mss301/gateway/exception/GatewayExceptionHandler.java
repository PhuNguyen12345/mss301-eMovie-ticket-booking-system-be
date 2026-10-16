package fu.mss301.gateway.exception;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class GatewayExceptionHandler implements WebExceptionHandler {
    private final GatewayErrorWriter writer;

    public GatewayExceptionHandler(GatewayErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (exchange.getResponse().isCommitted()) return Mono.error(exception);
        int status = exception instanceof ResponseStatusException responseException
            ? responseException.getStatusCode().value() : 502;
        String message = switch (status) {
            case 400 -> "Invalid request";
            case 404 -> "Route not found";
            case 503 -> "Service unavailable";
            case 504 -> "Service response timed out";
            default -> "Gateway request failed";
        };
        return writer.write(exchange, status, message);
    }
}
