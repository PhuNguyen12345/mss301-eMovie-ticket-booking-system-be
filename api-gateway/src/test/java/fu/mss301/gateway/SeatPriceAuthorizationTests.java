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
import java.util.Map;

import static org.mockito.Mockito.when;

@SpringJUnitConfig({SecurityConfig.class, SeatPriceAuthorizationTests.TestConfig.class})
@TestPropertySource(properties = {
    "gateway.jwt.secret=ZGV2ZWxvcG1lbnQtb25seS1jaGFuZ2UtdGhpcy1zZWNyZXQ=",
    "gateway.jwt.issuer=user-service",
    "gateway.jwt.audience=e-movie",
    "gateway.cors.allowed-origins=http://localhost:3000"
})
class SeatPriceAuthorizationTests {
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
    void anonymousCanReadPrices() {
        client.get().uri("/api/showtimes/7/seat-prices").exchange().expectStatus().isOk();
    }

    @Test
    void anonymousCannotReplacePrices() {
        client.put().uri("/api/showtimes/7/seat-prices").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("[]").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void customerCannotReplacePrices() {
        client.put().uri("/api/showtimes/7/seat-prices").headers(h -> h.setBearerAuth("CUSTOMER"))
            .contentType(MediaType.APPLICATION_JSON).bodyValue("[]")
            .exchange().expectStatus().isForbidden();
    }

    @Test
    void adminCanReplacePrices() {
        client.put().uri("/api/showtimes/7/seat-prices").headers(h -> h.setBearerAuth("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).bodyValue("[]")
            .exchange().expectStatus().isOk();
    }

    @Configuration
    @EnableWebFlux
    static class TestConfig {
        @Bean
        GatewayErrorWriter errors() {
            return new GatewayErrorWriter(JsonMapper.builder().build());
        }

        @Bean
        PriceEndpoint endpoint() {
            return new PriceEndpoint();
        }
    }

    @RestController
    static class PriceEndpoint {
        @RequestMapping(value = "/api/showtimes/{id}/seat-prices", method = {RequestMethod.GET, RequestMethod.PUT})
        Map<String, Object> prices() {
            return Map.of("status", 200, "data", List.of());
        }
    }
}
