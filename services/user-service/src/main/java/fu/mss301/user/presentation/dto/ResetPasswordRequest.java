package fu.mss301.user.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String otp,
        @NotBlank @Size(min = 8, max = 72) String newPassword
) {
    @Override
    public String toString() {
        return "ResetPasswordRequest[email=" + email + ", otp=[REDACTED], newPassword=[REDACTED]]";
    }
}
