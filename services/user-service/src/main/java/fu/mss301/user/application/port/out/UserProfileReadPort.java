package fu.mss301.user.application.port.out;

import fu.mss301.user.application.projection.UserProfileView;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserProfileReadPort {
    Optional<UserProfileView> findById(Integer userId);

    /** Profiles of the given users; unknown ids are left out. */
    List<UserProfileView> findAllById(Collection<Integer> userIds);
}
