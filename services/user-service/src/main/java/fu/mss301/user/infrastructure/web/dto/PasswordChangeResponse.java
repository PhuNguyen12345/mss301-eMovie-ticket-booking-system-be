package fu.mss301.user.infrastructure.web.dto;

import java.time.Instant;

public record PasswordChangeResponse(Instant changedAt) {
}
