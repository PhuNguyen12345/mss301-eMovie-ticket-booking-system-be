package fu.mss301.user.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record VerifyRegistrationRequest(
        @NotNull UUID registrationId,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String otp
) {
    @Override
    public String toString() {
        return "VerifyRegistrationRequest[registrationId=" + registrationId + ", otp=[REDACTED]]";
    }
}
