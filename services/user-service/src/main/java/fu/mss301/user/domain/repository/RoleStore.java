package fu.mss301.user.domain.repository;

import fu.mss301.user.domain.model.RoleName;
import fu.mss301.user.domain.model.UserRole;
import java.util.Optional;

public interface RoleStore {
    Optional<UserRole> findByName(RoleName name);
}
