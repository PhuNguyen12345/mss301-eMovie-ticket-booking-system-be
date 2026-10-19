package fu.mss301.user.infrastructure.persistence.adapter;

import fu.mss301.user.application.port.out.UserStore;
import fu.mss301.user.infrastructure.persistence.entity.User;
import fu.mss301.user.infrastructure.persistence.repository.RoleRepository;
import fu.mss301.user.infrastructure.persistence.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class JpaUserStore implements UserStore {
    private final UserRepository repository;
    private final RoleRepository roles;

    public JpaUserStore(UserRepository repository, RoleRepository roles) {
        this.repository = repository;
        this.roles = roles;
    }

    public Optional<fu.mss301.user.domain.model.User> findById(Integer id) { return repository.findById(id).map(user -> JpaDomainMapper.user(user, false)); }
    public Optional<fu.mss301.user.domain.model.User> findByEmail(String email) { return repository.findByEmail(email).map(user -> JpaDomainMapper.user(user, true)); }
    public Optional<fu.mss301.user.domain.model.User> findByIdForUpdate(Integer id) { return repository.findByIdForUpdate(id).map(user -> JpaDomainMapper.user(user, false)); }
    public Optional<Integer> findIdByEmail(String email) { return repository.findIdByEmail(email); }
    public fu.mss301.user.domain.model.User getReferenceById(Integer id) { return fu.mss301.user.domain.model.User.builder().id(id).build(); }
    public boolean existsByEmail(String email) { return repository.existsByEmail(email); }
    public boolean existsByPhone(String phone) { return repository.existsByPhone(phone); }
    public int updateLastLoginAt(Integer id, LocalDateTime at) { return repository.updateLastLoginAt(id, at); }
    public fu.mss301.user.domain.model.User saveAndFlush(fu.mss301.user.domain.model.User user) {
        User entity = repository.getReferenceById(user.getId());
        entity.setPasswordHash(user.getPasswordHash());
        entity.setUpdatedAt(user.getUpdatedAt());
        return JpaDomainMapper.user(repository.saveAndFlush(entity), false);
    }

    public fu.mss301.user.domain.model.User createAndFlush(NewUser value) {
        var assignedRoles = value.roles().stream().map(role -> roles.getReferenceById(role.id()))
                .collect(Collectors.toSet());
        User saved = repository.saveAndFlush(User.builder().fullName(value.fullName()).email(value.email())
                .phone(value.phone()).passwordHash(value.passwordHash()).status(value.status())
                .emailVerifiedAt(value.emailVerifiedAt()).createdAt(value.createdAt()).roles(assignedRoles).build());
        return JpaDomainMapper.user(saved, true);
    }
}
