package fu.mss301.user.infrastructure.web.dto;

import fu.mss301.user.domain.model.OtpDeliveryStatus;
import java.time.Instant;

public record RegistrationOtpResponse(String registrationId, Instant expiresAt,
        Instant resendAvailableAt, OtpDeliveryStatus otpDeliveryStatus) {
}
