package fu.mss301.user.application.port.out;

import fu.mss301.user.domain.model.RefreshCredential;
import fu.mss301.user.domain.model.User;
import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenStore {
    Optional<RefreshCredential> findByTokenHash(String hash);
    void update(RefreshCredential token);
    int revokeActiveByUserId(Integer userId, LocalDateTime now);
    void saveNew(NewRefreshToken token);

    record NewRefreshToken(User user, String tokenHash, LocalDateTime expiresAt,
                           LocalDateTime createdAt, Boolean revoked) {
        @Override public String toString() { return "NewRefreshToken[tokenHash=[REDACTED], revoked=" + revoked + "]"; }
    }
}
