package fu.mss301.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.Locale;

@Component
public class UserContextFilter implements GlobalFilter, Ordered {
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
