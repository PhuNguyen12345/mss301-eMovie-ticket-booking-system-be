package fu.mss301.user.application.usecase;

import fu.mss301.user.application.port.in.*;

import fu.mss301.user.application.usecase.*;
import fu.mss301.user.application.usecase.dto.LoginResponse.LoginUserResponse;
import fu.mss301.user.domain.model.RefreshCredential;
import fu.mss301.user.domain.model.UserRole;
import fu.mss301.user.domain.model.User;
import fu.mss301.user.domain.repository.RefreshTokenStore;
import fu.mss301.user.domain.repository.UserStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.stream.Collectors;

public class RefreshTokenUseCase implements RefreshTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenStore tokens;
    private final UserStore users;
    private final Clock clock;
    private final Duration ttl;

    public RefreshTokenUseCase(RefreshTokenStore tokens, UserStore users, Clock clock,
                               Duration ttl) {
        if (ttl.isZero() || ttl.isNegative() || ttl.getNano() != 0) {
            throw new IllegalArgumentException("Refresh token TTL must be a positive whole number of seconds");
        }
        this.tokens = tokens;
        this.users = users;
        this.clock = clock;
        this.ttl = ttl;
    }

    public IssuedRefreshToken issue(Integer userId) {
        return issueFor(users.getReferenceById(userId));
    }

    public RotatedRefreshToken rotate(String rawToken) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) {
            throw invalidToken();
        }
        RefreshCredential previous = tokens.findByTokenHash(hash(rawToken)).orElseThrow(this::invalidToken);
        var now = clock.instant();
        if (Boolean.TRUE.equals(previous.getRevoked())
                || !previous.getExpiresAt().isAfter(LocalDateTime.ofInstant(now, ZoneOffset.UTC))) {
            throw invalidToken();
        }
        User user = previous.getUser();
        if (!user.isActiveAndVerified()) {
            throw invalidToken();
        }
        previous.setRevoked(true);
        previous.setRevokedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        previous.setUpdatedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        tokens.update(previous);

        var responseUser = new LoginUserResponse(user.getId(), user.getFullName(), user.getEmail(),
                user.getRoles().stream().map(UserRole::name).collect(Collectors.toUnmodifiableSet()));
        return new RotatedRefreshToken(responseUser, issueFor(user));
    }

    private IssuedRefreshToken issueFor(User user) {
        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        var now = clock.instant();
        tokens.saveNew(new RefreshTokenStore.NewRefreshToken(user, hash(rawToken),
                LocalDateTime.ofInstant(now.plus(ttl), ZoneOffset.UTC),
                LocalDateTime.ofInstant(now, ZoneOffset.UTC), false));
        return new IssuedRefreshToken(rawToken, ttl.getSeconds());
    }

    public void revoke(String rawToken) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) return;
        tokens.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            if (Boolean.TRUE.equals(token.getRevoked())) return;
            var now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            token.setRevoked(true);
            token.setRevokedAt(now);
            token.setUpdatedAt(now);
            tokens.update(token);
        });
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private UseCaseException invalidToken() {
        return new UseCaseException(StatusCode.UNAUTHORIZED, "Invalid refresh token");
    }

}
