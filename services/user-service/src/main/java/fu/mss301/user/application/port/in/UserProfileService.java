package fu.mss301.user.application.port.in;

import fu.mss301.user.application.usecase.dto.UserProfileResponse;
import fu.mss301.user.application.usecase.dto.UserSummary;
import java.util.List;

public interface UserProfileService {
    UserProfileResponse me(Integer userId);

    /** Staff lookup of several customers at once, in the order asked; unknown ids are left out. */
    List<UserSummary> summaries(List<Integer> userIds);
}
