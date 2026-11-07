package fu.mss301.user.application.usecase.dto;

import fu.mss301.user.domain.model.OtpDeliveryStatus;
import java.time.Instant;

public record PasswordChangeOtpResponse(Instant expiresAt, Instant resendAvailableAt,
                                        OtpDeliveryStatus otpDeliveryStatus) {
}
