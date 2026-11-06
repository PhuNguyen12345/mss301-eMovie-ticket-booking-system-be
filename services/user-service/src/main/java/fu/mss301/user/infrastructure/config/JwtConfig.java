package fu.mss301.user.infrastructure.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {
    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(properties.secret());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("JWT_SECRET must be base64-encoded", ex);
        }
        if (key.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 base64-encoded bytes");
        }
        var jwk = new OctetSequenceKey.Builder(new SecretKeySpec(key, "HmacSHA256"))
                .algorithm(JWSAlgorithm.HS256).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }
}
