package fu.mss301.gateway;

import fu.mss301.gateway.config.SecurityConfig;
import fu.mss301.gateway.exception.GatewayErrorWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.config.EnableWebFlux;
import tools.jackson.databind.json.JsonMapper;

@SpringJUnitConfig({SecurityConfig.class, AuthEndpointAuthorizationTests.TestConfig.class})
@TestPropertySource(properties = {
    "gateway.jwt.secret=ZGV2ZWxvcG1lbnQtb25seS1jaGFuZ2UtdGhpcy1zZWNyZXQ=",
    "gateway.jwt.issuer=user-service",
    "gateway.jwt.audience=e-movie",
    "gateway.cors.allowed-origins=http://localhost:3000"
})
class AuthEndpointAuthorizationTests {
    private static final String[] PUBLIC_POST_PATHS = {
        "/api/auth/register/verify",
        "/api/auth/register/resend-otp",
        "/api/auth/password/forgot",
        "/api/auth/password/reset"
    };

    @Autowired private ApplicationContext context;

    @Test
    void anonymousRequestsReachRegistrationAndPasswordEndpoints() {
        WebTestClient client = WebTestClient.bindToApplicationContext(context).build();
        for (String path : PUBLIC_POST_PATHS) {
            client.post().uri(path).exchange().expectStatus().isOk();
        }
    }

    @Test
    void unrelatedAuthPostStillRequiresAuthorization() {
        WebTestClient client = WebTestClient.bindToApplicationContext(context).build();
        client.post().uri("/api/auth/unlisted").exchange().expectStatus().isUnauthorized();
    }

    @Configuration
    @EnableWebFlux
    static class TestConfig {
        @Bean
        GatewayErrorWriter errors() {
            return new GatewayErrorWriter(JsonMapper.builder().build());
        }

        @Bean
        AuthEndpoint endpoint() {
            return new AuthEndpoint();
        }
    }

    @RestController
    static class AuthEndpoint {
        @PostMapping({
            "/api/auth/register/verify",
            "/api/auth/register/resend-otp",
            "/api/auth/password/forgot",
            "/api/auth/password/reset"
        })
        String accepted() {
            return "accepted";
        }
    }
}
