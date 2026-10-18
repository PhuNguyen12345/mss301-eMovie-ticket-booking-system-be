package fu.mss301.user.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration accessTokenTtl,
        @NotBlank String keyId,
        @NotNull Resource privateKeyLocation,
        @NotNull Resource publicKeyLocation
) {
    public JwtProperties {
        if (accessTokenTtl != null && (accessTokenTtl.getSeconds() < 1 || accessTokenTtl.getNano() != 0)) {
            throw new IllegalArgumentException("JWT TTL must be a positive whole number of seconds");
        }
    }
}
