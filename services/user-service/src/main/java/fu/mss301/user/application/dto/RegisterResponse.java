package fu.mss301.user.application.dto;

public record RegisterResponse(
        Integer userId,
        String fullName,
        String email,
        String phone,
        String status
) {
}
