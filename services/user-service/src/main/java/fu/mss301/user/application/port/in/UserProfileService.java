package fu.mss301.user.application.port.in;

import fu.mss301.user.application.usecase.dto.UserProfileResponse;

public interface UserProfileService {
    UserProfileResponse me(Integer userId);
}
