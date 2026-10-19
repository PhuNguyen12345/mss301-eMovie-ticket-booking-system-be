package fu.mss301.user.infrastructure.security;

import fu.mss301.user.application.port.in.*;
import fu.mss301.user.infrastructure.config.JwtProperties;
import fu.mss301.user.application.dto.LoginResponse;
import fu.mss301.user.application.dto.LoginResponse.LoginUserResponse;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtServiceImpl implements JwtService {
    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtServiceImpl(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public LoginResponse issue(LoginUserResponse user) {
        var issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        var claims = JwtClaimsSet.builder()
                .subject(user.userId().toString())
                .issuer(properties.issuer()).audience(List.of(properties.audience()))
                .issuedAt(issuedAt).expiresAt(issuedAt.plus(properties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("roles", user.roles().stream().map(Enum::name).sorted().toList())
                .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256)
                .type("JWT").keyId(properties.keyId()).build();
        var token = encoder.encode(JwtEncoderParameters.from(header, claims));
        return new LoginResponse(token.getTokenValue(), "Bearer", properties.accessTokenTtl().getSeconds(),
                null, 0, user);
    }
}
