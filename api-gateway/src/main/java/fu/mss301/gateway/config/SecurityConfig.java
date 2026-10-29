package fu.mss301.gateway.config;

import fu.mss301.gateway.exception.GatewayErrorWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    private static final String[] CATALOG_PATHS = {
        "/api/movies/**", "/api/genres/**", "/api/persons/**", "/api/cinemas/**",
        "/api/screens/**", "/api/seats/**", "/api/showtimes/**"
    };

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, GatewayErrorWriter errors,
            CorsConfigurationSource corsSource) {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);

        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .logout(ServerHttpSecurity.LogoutSpec::disable)
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
            .cors(cors -> cors.configurationSource(corsSource))
            .authorizeExchange(auth -> auth
                .pathMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register", "/api/auth/refresh").permitAll()
                .pathMatchers(HttpMethod.GET, CATALOG_PATHS).permitAll()
                .pathMatchers(HttpMethod.HEAD, CATALOG_PATHS).permitAll()
                .pathMatchers(CATALOG_PATHS).hasRole("ADMIN")
                // PayOS calls the webhook without a JWT; booking-service verifies the checksum signature instead.
                .pathMatchers(HttpMethod.POST, "/api/payments/payos/webhook").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/bookings/showtimes/*/seats").permitAll()
                .pathMatchers("/api/payments/refunds", "/api/payments/refunds/**").hasRole("ADMIN")
                .pathMatchers("/api/users/me", "/api/users/me/**", "/api/bookings/**", "/api/payments/**").authenticated()
                .pathMatchers("/api/users/**").hasRole("ADMIN")
                .anyExchange().denyAll())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((exchange, exception) -> errors.write(exchange, 401, "Authentication required"))
                .accessDeniedHandler((exchange, exception) -> errors.write(exchange, 403, "Access denied")))
            .oauth2ResourceServer(resource -> resource
                .jwt(jwt -> jwt.jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(converter)))
                .authenticationEntryPoint((exchange, exception) -> errors.write(exchange, 401, "Invalid or expired access token"))
                .accessDeniedHandler((exchange, exception) -> errors.write(exchange, 403, "Access denied")))
            .build();
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(@Value("${gateway.jwt.secret}") String secret,
            @Value("${gateway.jwt.issuer}") String issuer, @Value("${gateway.jwt.audience}") String audience) {
        byte[] key = Base64.getDecoder().decode(secret);
        if (key.length < 32) throw new IllegalArgumentException("JWT_SECRET must contain at least 32 base64-encoded bytes");
        var decoder = NimbusReactiveJwtDecoder.withSecretKey(new SecretKeySpec(key, "HmacSHA256"))
            .macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            Object roles = jwt.getClaims().get("roles");
            boolean validRoles = roles instanceof List<?> values && values.stream()
                .allMatch(value -> value instanceof String role && role.matches("[A-Z][A-Z0-9_]{0,63}"));
            if (jwt.getExpiresAt() == null || jwt.getSubject() == null
                    || !jwt.getSubject().matches("[A-Za-z0-9][A-Za-z0-9._:@-]{0,127}")
                    || jwt.getAudience() == null || !jwt.getAudience().contains(audience) || !validRoles) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid access token claims", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), claims));
        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${gateway.cors.allowed-origins}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setExposedHeaders(List.of("Location"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
