package fu.mss301.user.infrastructure.persistence.adapter;

import fu.mss301.user.application.port.out.RoleStore;
import fu.mss301.user.domain.model.RoleName;
import fu.mss301.user.domain.model.UserRole;
import fu.mss301.user.infrastructure.persistence.repository.RoleRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaRoleStore implements RoleStore {
    private final RoleRepository repository;

    public JpaRoleStore(RoleRepository repository) { this.repository = repository; }
    public Optional<UserRole> findByName(RoleName name) {
        return repository.findByName(name).map(JpaDomainMapper::role);
    }
}
