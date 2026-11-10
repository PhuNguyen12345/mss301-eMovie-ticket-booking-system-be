package fu.mss301.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.Base64;
import java.util.Locale;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@Component
public class UserContextFilter implements GlobalFilter, Ordered {
    private final String gatewayKey;

    public UserContextFilter(@Value("${gateway.shared-secret}") String sharedSecret) {
        try {
            if (Base64.getDecoder().decode(sharedSecret).length < 32) {
                throw new IllegalArgumentException("Key is too short");
            }
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("GATEWAY_SHARED_SECRET must be a base64-encoded key of at least 32 bytes");
        }
        gatewayKey = sharedSecret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.<Principal>getPrincipal().map(principal -> withIdentity(exchange, principal))
            .defaultIfEmpty(withIdentity(exchange, null)).flatMap(chain::filter);
    }

    private ServerWebExchange withIdentity(ServerWebExchange exchange, Principal principal) {
        var request = exchange.getRequest().mutate().headers(headers -> {
            headers.headerNames().stream().filter(name -> {
                String normalized = name.toLowerCase(Locale.ROOT);
                return normalized.startsWith("x-user-") || normalized.equals("x-role")
                    || normalized.equals("x-roles") || normalized.equals("x-authenticated-user");
            }).toList().forEach(headers::remove);
            headers.remove(HttpHeaders.AUTHORIZATION);
            headers.remove("X-Gateway-Key");
            Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
            if (route != null && "user-service".equals(route.getId())) {
                headers.set("X-Gateway-Key", gatewayKey);
            }
            if (principal instanceof JwtAuthenticationToken authentication && authentication.isAuthenticated()) {
                headers.set("X-User-Id", authentication.getToken().getSubject());
                headers.set("X-User-Roles", String.join(",", authentication.getToken().getClaimAsStringList("roles")));
            }
        }).build();
        return exchange.mutate().request(request).build();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
