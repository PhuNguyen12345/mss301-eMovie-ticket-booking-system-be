package fu.mss301.user.infrastructure.web.dto;

public record UserSummaryResponse(Integer userId, String fullName, String email, String phone, String status) {
}
