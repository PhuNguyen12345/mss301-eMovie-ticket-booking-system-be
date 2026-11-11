package fu.mss301.user.application.usecase.dto;

/** What staff see of a customer next to an order: who it is and how to reach them. */
public record UserSummary(Integer userId, String fullName, String email, String phone, String status) {
}
