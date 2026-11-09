package fu.mss301.user.domain.model;

import java.time.Duration;
import java.time.LocalDateTime;
public enum OtpState {
    READY,
    USED,
    EXPIRED,
    ATTEMPTS_EXHAUSTED,
    RESEND_LIMIT_REACHED,
    COOLDOWN;
    static long remainingSeconds(LocalDateTime now, LocalDateTime availableAt) {
        if (!availableAt.isAfter(now))
            return 0;
        Duration remaining = Duration.between(now, availableAt);
        return Math.max(1, remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0));
    }
}
