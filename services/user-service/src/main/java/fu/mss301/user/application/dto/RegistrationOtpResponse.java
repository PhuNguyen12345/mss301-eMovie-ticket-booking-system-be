package fu.mss301.user.application.dto;

import fu.mss301.user.domain.model.OtpDeliveryStatus;
import java.time.Instant;

public record RegistrationOtpResponse(String registrationId, Instant expiresAt,
        Instant resendAvailableAt, OtpDeliveryStatus otpDeliveryStatus) {
}
