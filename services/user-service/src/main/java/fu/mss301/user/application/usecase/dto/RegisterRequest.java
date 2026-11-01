package fu.mss301.user.application.usecase.dto;

public record RegisterRequest( String fullName, String email,
        String phone, String password
) {
}
