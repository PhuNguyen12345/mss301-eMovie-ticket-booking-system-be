package fu.mss301.user.application.dto;

import java.time.LocalDate;

public record UserProfileResponse(
        Integer userId,
        String fullName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        String gender,
        String cityProvince,
        String district,
        String address,
        Integer preferredCinemaId,
        String avatarUrl
) {
}
