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

@SpringJUnitConfig({SecurityConfig.class, ComboAuthorizationTests.TestConfig.class})
@TestPropertySource(properties = {
    "gateway.jwt.secret=ZGV2ZWxvcG1lbnQtb25seS1jaGFuZ2UtdGhpcy1zZWNyZXQ=",
    "gateway.jwt.issuer=user-service",
    "gateway.jwt.audience=e-movie",
    "gateway.cors.allowed-origins=http://localhost:3000"
})
class ComboAuthorizationTests {
    private static final String BODY = "{\"name\":\"Bắp nước\",\"price\":85000}";

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
    void anonymousCanReadTheMenu() {
        client.get().uri("/api/combos").exchange().expectStatus().isOk();
    }

    @Test
    void theFullListIsNotPublic() {
        client.get().uri("/api/combos/all").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/combos/all").headers(h -> h.setBearerAuth("CUSTOMER"))
            .exchange().expectStatus().isForbidden();
        client.get().uri("/api/combos/all").headers(h -> h.setBearerAuth("ADMIN"))
            .exchange().expectStatus().isOk();
    }

    @Test
    void anonymousCannotManageCombos() {
        client.post().uri("/api/combos").contentType(MediaType.APPLICATION_JSON).bodyValue(BODY)
            .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void customerCannotManageCombos() {
        client.post().uri("/api/combos").headers(h -> h.setBearerAuth("CUSTOMER"))
            .contentType(MediaType.APPLICATION_JSON).bodyValue(BODY).exchange().expectStatus().isForbidden();
        client.put().uri("/api/combos/5").headers(h -> h.setBearerAuth("CUSTOMER"))
            .contentType(MediaType.APPLICATION_JSON).bodyValue(BODY).exchange().expectStatus().isForbidden();
        client.post().uri("/api/combos/5/deactivate").headers(h -> h.setBearerAuth("CUSTOMER"))
            .exchange().expectStatus().isForbidden();
    }

    @Test
    void adminCanManageCombos() {
        client.post().uri("/api/combos").headers(h -> h.setBearerAuth("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).bodyValue(BODY).exchange().expectStatus().isOk();
        client.put().uri("/api/combos/5").headers(h -> h.setBearerAuth("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).bodyValue(BODY).exchange().expectStatus().isOk();
        client.post().uri("/api/combos/5/deactivate").headers(h -> h.setBearerAuth("ADMIN"))
            .exchange().expectStatus().isOk();
    }

    @Test
    void customerCanChangeCombosOfABookingOnlyWhenSignedIn() {
        client.put().uri("/api/bookings/1/combos").contentType(MediaType.APPLICATION_JSON).bodyValue("{\"combos\":[]}")
            .exchange().expectStatus().isUnauthorized();
        client.put().uri("/api/bookings/1/combos").headers(h -> h.setBearerAuth("CUSTOMER"))
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"combos\":[]}").exchange().expectStatus().isOk();
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
        @GetMapping({"/api/combos", "/api/combos/all"})
        Mono<String> read() {
            return Mono.just("ok");
        }

        @PostMapping({"/api/combos", "/api/combos/{id}/deactivate"})
        Mono<String> create() {
            return Mono.just("ok");
        }

        @PutMapping({"/api/combos/{id}", "/api/bookings/{id}/combos"})
        Mono<String> update() {
            return Mono.just("ok");
        }
    }
}
