package fu.mss301.user.application.dto;

public record RegisterRequest( String fullName, String email,
        String phone, String password
) {
}
