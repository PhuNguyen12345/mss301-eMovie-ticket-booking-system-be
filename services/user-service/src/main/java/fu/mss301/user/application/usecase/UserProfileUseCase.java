package fu.mss301.user.application.usecase;

import fu.mss301.user.application.port.in.*;

import fu.mss301.user.application.usecase.*;
import fu.mss301.user.application.usecase.dto.UserProfileResponse;
import fu.mss301.user.application.port.out.UserProfileReadPort;

public class UserProfileUseCase implements UserProfileService {
    private final UserProfileReadPort profiles;

    public UserProfileUseCase(UserProfileReadPort profiles) {
        this.profiles = profiles;
    }

    public UserProfileResponse me(Integer userId) {
        var profile = profiles.findById(userId)
                .orElseThrow(() -> new UseCaseException(StatusCode.NOT_FOUND, "UserAccount not found"));
        if (!profile.isActiveAndVerified()) {
            throw new UseCaseException(StatusCode.FORBIDDEN, "Account is not active or email is not verified");
        }
        return profile.toResponse();
    }
}
