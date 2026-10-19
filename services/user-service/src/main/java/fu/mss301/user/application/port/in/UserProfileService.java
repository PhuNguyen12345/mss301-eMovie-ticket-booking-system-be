package fu.mss301.user.application.port.in;

import fu.mss301.user.application.dto.UserProfileResponse;

public interface UserProfileService {
    UserProfileResponse me(Integer userId);
}
