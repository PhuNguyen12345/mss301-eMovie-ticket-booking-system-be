package fu.mss301.user.application.dto;

public record PasswordChangeOtpRequest( String currentPassword) {
    @Override
    public String toString() {
        return "PasswordChangeOtpRequest[currentPassword=[REDACTED]]";
    }
}
