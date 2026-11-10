package fu.mss301.gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "gateway.jwt.secret=ZGV2ZWxvcG1lbnQtb25seS1jaGFuZ2UtdGhpcy1zZWNyZXQ=",
    "gateway.shared-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    "eureka.client.enabled=false"
})
class UserGatewayIntegrationTests {
    private static final String JWT_KEY = "ZGV2ZWxvcG1lbnQtb25seS1jaGFuZ2UtdGhpcy1zZWNyZXQ=";
    private static final String GATEWAY_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final DisposableServer UPSTREAM = HttpServer.create().host("127.0.0.1").port(0)
        .handle((request, response) -> response.header("Content-Type", "application/json")
            .sendString(Mono.just(JsonMapper.builder().build().writeValueAsString(Map.of(
                "path", request.uri(),
                "gatewayKey", request.requestHeaders().get("X-Gateway-Key", ""),
                "userId", request.requestHeaders().get("X-User-Id", ""),
                "roles", request.requestHeaders().get("X-User-Roles", ""),
                "authorization", request.requestHeaders().get("Authorization", ""))))))
        .bindNow();

    @LocalServerPort private int port;
    private WebTestClient client;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry properties) {
        properties.add("USER_SERVICE_URI", () -> "http://127.0.0.1:" + UPSTREAM.port());
        properties.add("MOVIE_SERVICE_URI", () -> "http://127.0.0.1:" + UPSTREAM.port());
    }

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }

    @AfterAll
    static void closeUpstream() {
        UPSTREAM.disposeNow();
    }

    @Test
    void signedCustomerTokenReplacesForgedIdentityAndGatewayKey() throws Exception {
        client.get().uri("/api/users/me").header("Authorization", "Bearer " + token("e-movie"))
            .header("X-User-Id", "999").header("X-User-Roles", "ADMIN")
            .header("X-Gateway-Key", "attacker", "second-value")
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.path").isEqualTo("/api/users/me")
            .jsonPath("$.gatewayKey").isEqualTo(GATEWAY_KEY)
            .jsonPath("$.userId").isEqualTo("42")
            .jsonPath("$.roles").isEqualTo("CUSTOMER")
            .jsonPath("$.authorization").isEqualTo("");
    }

    @Test
    void publicAuthRoutesReachUserServiceWithoutForwardingForgedIdentity() {
        for (String path : List.of("login", "register", "refresh", "logout", "register/verify",
                "register/resend-otp", "password/forgot", "password/reset")) {
            client.post().uri("/api/auth/" + path)
                .header("X-User-Id", "999").header("X-User-Roles", "ADMIN").header("X-Gateway-Key", "attacker")
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.path").isEqualTo("/api/auth/" + path)
                .jsonPath("$.gatewayKey").isEqualTo(GATEWAY_KEY)
                .jsonPath("$.userId").isEqualTo("")
                .jsonPath("$.roles").isEqualTo("");
        }
    }

    @Test
    void userServiceKeyIsNotForwardedToOtherServices() {
        client.get().uri("/api/movies").header("X-Gateway-Key", "attacker")
            .exchange().expectStatus().isOk().expectBody().jsonPath("$.gatewayKey").isEqualTo("");
    }

    @Test
    void missingTokenWrongAudienceAndCustomerAdminAccessAreRejected() throws Exception {
        client.get().uri("/api/users/me").header("X-User-Id", "42").header("X-User-Roles", "ADMIN")
            .exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/users/me").header("Authorization", "Bearer " + token("other-app"))
            .exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/users/42").header("Authorization", "Bearer " + token("e-movie"))
            .exchange().expectStatus().isForbidden();
    }

    private String token(String audience) throws Exception {
        Instant now = Instant.now();
        var token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
            .subject("42").issuer("user-service").audience(audience)
            .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300)))
            .claim("roles", List.of("CUSTOMER")).build());
        token.sign(new MACSigner(Base64.getDecoder().decode(JWT_KEY)));
        return token.serialize();
    }
}
