package fu.mss301.gateway;

import fu.mss301.gateway.config.SecurityConfig;
import fu.mss301.gateway.exception.GatewayErrorWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.config.EnableWebFlux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.mockito.Mockito.when;

@SpringJUnitConfig({SecurityConfig.class, CheckInAuthorizationTests.TestConfig.class})
@TestPropertySource(properties = {
    "gateway.jwt.secret=ZGV2ZWxvcG1lbnQtb25seS1jaGFuZ2UtdGhpcy1zZWNyZXQ=",
    "gateway.jwt.issuer=user-service",
    "gateway.jwt.audience=e-movie",
    "gateway.cors.allowed-origins=http://localhost:3000"
})
/** Door staff and admin-only booking actions. */
class CheckInAuthorizationTests {
    private static final String BODY = "{\"bookingCode\":\"BK1\"}";

    @Autowired private ApplicationContext context;
    @MockitoBean private ReactiveJwtDecoder jwtDecoder;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context).build();
        for (String role : List.of("ADMIN", "CUSTOMER")) {
            when(jwtDecoder.decode(role)).thenReturn(Mono.just(Jwt.withTokenValue(role)
                .header("alg", "HS256").subject("42").claim("roles", List.of(role)).build()));
        }
    }

    @Test
    void onlyAdminsCheckTicketsIn() {
        client.post().uri("/api/bookings/check-in").contentType(MediaType.APPLICATION_JSON).bodyValue(BODY)
            .exchange().expectStatus().isUnauthorized();
        client.post().uri("/api/bookings/check-in").headers(h -> h.setBearerAuth("CUSTOMER"))
            .contentType(MediaType.APPLICATION_JSON).bodyValue(BODY).exchange().expectStatus().isForbidden();
        client.post().uri("/api/bookings/check-in").headers(h -> h.setBearerAuth("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).bodyValue(BODY).exchange().expectStatus().isOk();
    }

    @Test
    void onlyAdminsReRunACancelledShowtime() {
        client.post().uri("/api/bookings/showtimes/15/cancellation").exchange().expectStatus().isUnauthorized();
        client.post().uri("/api/bookings/showtimes/15/cancellation").headers(h -> h.setBearerAuth("CUSTOMER"))
            .exchange().expectStatus().isForbidden();
        client.post().uri("/api/bookings/showtimes/15/cancellation").headers(h -> h.setBearerAuth("ADMIN"))
            .exchange().expectStatus().isOk();
        client.get().uri("/api/bookings/showtimes/15/seats").exchange().expectStatus().isOk();
    }

    @Test
    void onlyAdminsSeeStatistics() {
        client.get().uri("/api/bookings/statistics?from=2026-11-01&to=2026-11-30").exchange()
            .expectStatus().isUnauthorized();
        client.get().uri("/api/bookings/statistics?from=2026-11-01&to=2026-11-30")
            .headers(h -> h.setBearerAuth("CUSTOMER")).exchange().expectStatus().isForbidden();
        client.get().uri("/api/bookings/statistics?from=2026-11-01&to=2026-11-30")
            .headers(h -> h.setBearerAuth("ADMIN")).exchange().expectStatus().isOk();
    }

    @Test
    void theInternalPathIsNotReachableThroughTheGateway() {
        client.post().uri("/internal/showtimes/15/cancellation").headers(h -> h.setBearerAuth("ADMIN"))
            .exchange().expectStatus().isForbidden();
    }

    @Test
    void customersStillUseTheirOwnBookingPaths() {
        client.get().uri("/api/bookings/7/qr").headers(h -> h.setBearerAuth("CUSTOMER")).exchange().expectStatus().isOk();
        client.get().uri("/api/bookings/7/qr").exchange().expectStatus().isUnauthorized();
        client.post().uri("/api/bookings/7/cancel").headers(h -> h.setBearerAuth("CUSTOMER"))
            .exchange().expectStatus().isOk();
    }

    @Configuration
    @EnableWebFlux
    static class TestConfig {
        @Bean
        GatewayErrorWriter gatewayErrorWriter() {
            return new GatewayErrorWriter(JsonMapper.builder().build());
        }

        @Bean
        StubController stubController() {
            return new StubController();
        }
    }

    @RestController
    static class StubController {
        @GetMapping("/api/bookings/{id}/qr")
        Mono<String> qr() {
            return Mono.just("ok");
        }

        @GetMapping("/api/bookings/statistics")
        Mono<String> statistics() {
            return Mono.just("ok");
        }

        @GetMapping("/api/bookings/showtimes/{id}/seats")
        Mono<String> seats() {
            return Mono.just("ok");
        }

        @PostMapping({"/api/bookings/check-in", "/api/bookings/{id}/cancel", "/api/bookings/showtimes/{id}/cancellation",
            "/internal/showtimes/{id}/cancellation"})
        Mono<String> write() {
            return Mono.just("ok");
        }
    }
}
