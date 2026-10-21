package fu.mss301.user.application.dto;

public record ConfirmPasswordChangeRequest( String otp, String newPassword
) {
    @Override
    public String toString() {
        return "ConfirmPasswordChangeRequest[otp=[REDACTED], newPassword=[REDACTED]]";
    }
}
