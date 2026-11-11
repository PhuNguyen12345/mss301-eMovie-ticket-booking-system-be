package fu.mss301.user.application.projection;

import fu.mss301.user.application.usecase.dto.UserProfileResponse;
import fu.mss301.user.application.usecase.dto.UserSummary;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Read-side view of the fields needed by the profile endpoint. */
public record UserProfileView(
        Integer userId, String fullName, String email, String phone,
        LocalDate dateOfBirth, String gender, String cityProvince, String district,
        String address, Integer preferredCinemaId, String avatarUrl,
        String status, LocalDateTime emailVerifiedAt) {

    public boolean isActiveAndVerified() {
        return "ACTIVE".equals(status) && emailVerifiedAt != null;
    }

    public UserSummary toSummary() {
        return new UserSummary(userId, fullName, email, phone, status);
    }

    public UserProfileResponse toResponse() {
        return new UserProfileResponse(userId, fullName, email, phone, dateOfBirth,
                gender, cityProvince, district, address, preferredCinemaId, avatarUrl);
    }
}
