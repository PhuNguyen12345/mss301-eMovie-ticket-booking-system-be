package fu.mss301.user.application.dto;

import java.time.Instant;

public record PasswordChangeResponse(Instant changedAt) {
}
