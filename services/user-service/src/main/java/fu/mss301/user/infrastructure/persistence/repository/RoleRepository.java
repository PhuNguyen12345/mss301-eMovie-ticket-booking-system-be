package fu.mss301.user.infrastructure.persistence.repository;

import fu.mss301.user.infrastructure.persistence.entity.Role;
import fu.mss301.user.domain.model.RoleName;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> {
    Optional<Role> findByName(RoleName name);
}
