package fu.mss301.user.application.usecase.dto;

public record RefreshRequest( String refreshToken) {
    @Override
    public String toString() {
        return "RefreshRequest[refreshToken=[REDACTED]]";
    }
}
