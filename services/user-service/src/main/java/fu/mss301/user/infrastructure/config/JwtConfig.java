package fu.mss301.user.infrastructure.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {
    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        RSAPrivateKey privateKey;
        RSAPublicKey publicKey;
        try (var privateStream = properties.privateKeyLocation().getInputStream();
             var publicStream = properties.publicKeyLocation().getInputStream()) {
            privateKey = RsaKeyConverters.pkcs8().convert(privateStream);
            publicKey = RsaKeyConverters.x509().convert(publicStream);
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalStateException("Unable to load JWT RSA keys; check key locations and PEM formats");
        }
        if (privateKey == null || publicKey == null || publicKey.getModulus().bitLength() < 2048
                || !publicKey.getModulus().equals(privateKey.getModulus())
                || (privateKey instanceof RSAPrivateCrtKey crt
                    && !publicKey.getPublicExponent().equals(crt.getPublicExponent()))) {
            throw new IllegalStateException("JWT requires a matching RSA key pair of at least 2048 bits");
        }
        RSAKey key = new RSAKey.Builder(publicKey).privateKey(privateKey)
                .keyID(properties.keyId()).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

}
