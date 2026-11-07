package fu.mss301.user.application.port.out;

import fu.mss301.user.application.projection.UserProfileView;
import java.util.Optional;

public interface UserProfileReadPort {
    Optional<UserProfileView> findById(Integer userId);
}
