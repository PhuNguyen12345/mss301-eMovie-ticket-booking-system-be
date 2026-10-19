package fu.mss301.user.application.dto;

public record RefreshRequest( String refreshToken) {
    @Override
    public String toString() {
        return "RefreshRequest[refreshToken=[REDACTED]]";
    }
}
