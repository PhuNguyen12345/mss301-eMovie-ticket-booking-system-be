package fu.mss301.user.application.service;

import fu.mss301.user.infrastructure.security.JwtServiceImpl;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import fu.mss301.user.infrastructure.config.JwtConfig;
import fu.mss301.user.infrastructure.config.JwtProperties;
import fu.mss301.user.application.dto.LoginResponse.LoginUserResponse;
import fu.mss301.user.domain.model.RoleName;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class JwtServiceTest {
    private static KeyPair keys;

    @BeforeAll
    static void generateKeys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
    }

    private JwtProperties properties(KeyPair keyPair) {
        return new JwtProperties("user-service", "e-movie-api", Duration.ofMinutes(15), "test-key",
                pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()), pem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
    }

    private ByteArrayResource pem(String type, byte[] encoded) {
        String value = "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
                + "\n-----END " + type + "-----\n";
        return new ByteArrayResource(value.getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void issuesRsaSignedTokenWithExpectedClaimsAndLifetime() throws Exception {
        var properties = properties(keys);
        Instant now = Instant.parse("2026-10-12T07:00:00Z");
        var service = new JwtServiceImpl(new JwtConfig().jwtEncoder(properties), properties,
                Clock.fixed(now, ZoneOffset.UTC));
        var user = new LoginUserResponse(42, "Customer", "customer@example.com", Set.of(RoleName.CUSTOMER));
        var response = service.issue(user);
        var token = SignedJWT.parse(response.accessToken());
        assertEquals(JWSAlgorithm.RS256, token.getHeader().getAlgorithm());
        assertEquals("test-key", token.getHeader().getKeyID());
        // Verify the signature independently of the application's decoder.
        assertTrue(token.verify(new RSASSAVerifier((RSAPublicKey) keys.getPublic())));
        var claims = token.getJWTClaimsSet();
        assertEquals("42", claims.getSubject());
        assertEquals("user-service", claims.getIssuer());
        assertEquals(List.of("e-movie-api"), claims.getAudience());
        assertEquals(List.of("CUSTOMER"), claims.getStringListClaim("roles"));
        assertEquals(now, claims.getIssueTime().toInstant());
        assertEquals(now.plusSeconds(900), claims.getExpirationTime().toInstant());
        assertNotEquals(claims.getJWTID(), SignedJWT.parse(service.issue(user).accessToken()).getJWTClaimsSet().getJWTID());
        assertEquals("Bearer", response.tokenType());
        assertEquals(900, response.expiresIn());
        assertFalse(response.toString().contains(response.accessToken()));
    }

    @Test
    void rejectsMismatchedKeyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var other = generator.generateKeyPair();
        var properties = new JwtProperties("issuer", "audience", Duration.ofMinutes(15), "key",
                pem("PRIVATE KEY", keys.getPrivate().getEncoded()), pem("PUBLIC KEY", other.getPublic().getEncoded()));
        assertThrows(IllegalStateException.class, () -> new JwtConfig().jwtEncoder(properties));
    }

    @Test
    void rejectsNonpositiveTtl() {
        assertThrows(IllegalArgumentException.class, () -> new JwtProperties("issuer", "audience", Duration.ZERO,
                "key", pem("PRIVATE KEY", keys.getPrivate().getEncoded()), pem("PUBLIC KEY", keys.getPublic().getEncoded())));
    }

}
