package fu.mss301.user.application.usecase.dto;

public record ResetPasswordRequest( String email, String otp, String newPassword
) {
    @Override
    public String toString() {
        return "ResetPasswordRequest[email=" + email + ", otp=[REDACTED], newPassword=[REDACTED]]";
    }
}
