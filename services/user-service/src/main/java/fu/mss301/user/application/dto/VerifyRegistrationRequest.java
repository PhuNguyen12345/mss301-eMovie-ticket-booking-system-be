package fu.mss301.user.application.dto;

import java.util.UUID;

public record VerifyRegistrationRequest( UUID registrationId, String otp
) {
    @Override
    public String toString() {
        return "VerifyRegistrationRequest[registrationId=" + registrationId + ", otp=[REDACTED]]";
    }
}
