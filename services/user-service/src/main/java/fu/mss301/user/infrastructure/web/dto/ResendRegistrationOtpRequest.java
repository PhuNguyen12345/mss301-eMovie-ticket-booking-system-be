package fu.mss301.user.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ResendRegistrationOtpRequest(@NotNull UUID registrationId) {
}
