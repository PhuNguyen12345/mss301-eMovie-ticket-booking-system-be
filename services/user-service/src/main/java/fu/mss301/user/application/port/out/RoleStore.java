package fu.mss301.user.application.port.out;

import fu.mss301.user.domain.model.RoleName;
import fu.mss301.user.domain.model.UserRole;
import java.util.Optional;

public interface RoleStore {
    Optional<UserRole> findByName(RoleName name);
}
