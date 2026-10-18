package fu.mss301.user.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordChangeOtpRequest(@NotBlank String currentPassword) {
    @Override
    public String toString() {
        return "PasswordChangeOtpRequest[currentPassword=[REDACTED]]";
    }
}
