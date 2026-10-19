package fu.mss301.user.application.dto;

public record ResetPasswordRequest( String email, String otp, String newPassword
) {
    @Override
    public String toString() {
        return "ResetPasswordRequest[email=" + email + ", otp=[REDACTED], newPassword=[REDACTED]]";
    }
}
