package fu.mss301.user.application.service;

import fu.mss301.user.application.port.in.*;
import fu.mss301.common.exception.ApiException;
import fu.mss301.user.application.dto.UserProfileResponse;
import fu.mss301.user.application.port.out.UserStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileUseCase implements UserProfileService {
    private final UserStore users;

    public UserProfileUseCase(UserStore users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse me(Integer userId) {
        var user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "UserAccount not found"));
        if (!user.isActiveAndVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is not active or email is not verified");
        }
        return new UserProfileResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getDateOfBirth(), user.getGender(), user.getCityProvince(), user.getDistrict(),
                user.getAddress(), user.getPreferredCinemaId(), user.getAvatarUrl());
    }
}
