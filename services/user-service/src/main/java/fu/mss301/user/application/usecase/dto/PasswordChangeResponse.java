package fu.mss301.user.application.usecase.dto;

import java.time.Instant;

public record PasswordChangeResponse(Instant changedAt) {
}
