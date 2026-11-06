package fu.mss301.user.application.usecase;

import fu.mss301.user.infrastructure.security.JwtServiceImpl;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import fu.mss301.user.infrastructure.config.JwtConfig;
import fu.mss301.user.infrastructure.config.JwtProperties;
import fu.mss301.user.application.usecase.dto.LoginResponse.LoginUserResponse;
import fu.mss301.user.domain.model.RoleName;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class JwtServiceTest {
    private static final byte[] SECRET = "development-only-change-this-secret"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private JwtProperties properties() {
        return new JwtProperties("user-service", "e-movie", Duration.ofMinutes(15),
                Base64.getEncoder().encodeToString(SECRET));
    }

    @Test
    void issuesGatewayCompatibleTokenWithExpectedClaimsAndLifetime() throws Exception {
        var properties = properties();
        Instant now = Instant.parse("2026-10-12T07:00:00Z");
        var service = new JwtServiceImpl(new JwtConfig().jwtEncoder(properties), properties,
                Clock.fixed(now, ZoneOffset.UTC));
        var user = new LoginUserResponse(42, "Customer", "customer@example.com", Set.of(RoleName.CUSTOMER));
        var response = service.issue(user);
        var token = SignedJWT.parse(response.accessToken());
        assertEquals(JWSAlgorithm.HS256, token.getHeader().getAlgorithm());
        assertTrue(token.verify(new MACVerifier(SECRET)));
        var claims = token.getJWTClaimsSet();
        assertEquals("42", claims.getSubject());
        assertEquals("user-service", claims.getIssuer());
        assertEquals(List.of("e-movie"), claims.getAudience());
        assertEquals(List.of("CUSTOMER"), claims.getStringListClaim("roles"));
        assertEquals(now, claims.getIssueTime().toInstant());
        assertEquals(now.plusSeconds(900), claims.getExpirationTime().toInstant());
        assertNotEquals(claims.getJWTID(), SignedJWT.parse(service.issue(user).accessToken()).getJWTClaimsSet().getJWTID());
        assertEquals("Bearer", response.tokenType());
        assertEquals(900, response.expiresIn());
        assertFalse(response.toString().contains(response.accessToken()));
    }

    @Test
    void gatewayDecoderAcceptsIssuedToken() {
        var properties = properties();
        var service = new JwtServiceImpl(new JwtConfig().jwtEncoder(properties), properties, Clock.systemUTC());
        var user = new LoginUserResponse(42, "Customer", "customer@example.com", Set.of(RoleName.CUSTOMER));
        var gatewayDecoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(SECRET, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();

        assertEquals("42", gatewayDecoder.decode(service.issue(user).accessToken()).getSubject());
    }

    @Test
    void rejectsTooShortSecret() {
        var properties = new JwtProperties("issuer", "audience", Duration.ofMinutes(15),
                Base64.getEncoder().encodeToString(new byte[31]));
        assertThrows(IllegalArgumentException.class, () -> new JwtConfig().jwtEncoder(properties));
    }

    @Test
    void rejectsInvalidBase64Secret() {
        var properties = new JwtProperties("issuer", "audience", Duration.ofMinutes(15), "invalid-secret");
        assertThrows(IllegalArgumentException.class, () -> new JwtConfig().jwtEncoder(properties));
    }

    @Test
    void rejectsNonpositiveTtl() {
        assertThrows(IllegalArgumentException.class, () -> new JwtProperties("issuer", "audience", Duration.ZERO,
                Base64.getEncoder().encodeToString(SECRET)));
    }

}
