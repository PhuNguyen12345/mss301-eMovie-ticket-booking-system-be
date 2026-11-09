package fu.mss301.user.domain.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@ToString
public class RefreshCredential {
    private Integer id;
    private User user;
    @Builder.Default private Boolean revoked = false;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private LocalDateTime updatedAt;
    public boolean canRotate(LocalDateTime now) {
        return !Boolean.TRUE.equals(revoked) && expiresAt.isAfter(now) && user.isActiveAndVerified();
    }
    public void consume(LocalDateTime now) {
        if (!canRotate(now))
            throw new fu.mss301.user.domain.exception.DomainException("Invalid refresh token");
        revoked = true;
        revokedAt = now;
        updatedAt = now;
    }
}
