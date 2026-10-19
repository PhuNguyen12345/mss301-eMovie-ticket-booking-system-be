package fu.mss301.user.infrastructure.persistence.adapter;

import fu.mss301.user.application.port.out.RefreshTokenStore;
import fu.mss301.user.domain.model.RefreshCredential;
import fu.mss301.user.infrastructure.persistence.entity.RefreshToken;
import fu.mss301.user.infrastructure.persistence.repository.RefreshTokenRepository;
import fu.mss301.user.infrastructure.persistence.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaRefreshTokenStore implements RefreshTokenStore {
    private final RefreshTokenRepository repository;
    private final UserRepository users;

    public JpaRefreshTokenStore(RefreshTokenRepository repository, UserRepository users) {
        this.repository = repository;
        this.users = users;
    }
    public Optional<RefreshCredential> findByTokenHash(String hash) {
        return repository.findByTokenHash(hash).map(JpaDomainMapper::refreshCredential);
    }
    public void update(RefreshCredential value) {
        RefreshToken entity = repository.getReferenceById(value.getId());
        entity.setRevoked(value.getRevoked());
        entity.setRevokedAt(value.getRevokedAt());
        entity.setUpdatedAt(value.getUpdatedAt());
    }
    public int revokeActiveByUserId(Integer userId, LocalDateTime now) { return repository.revokeActiveByUserId(userId, now); }
    public void saveNew(NewRefreshToken value) {
        repository.save(RefreshToken.builder().user(users.getReferenceById(value.user().getId()))
                .tokenHash(value.tokenHash())
                .expiresAt(value.expiresAt()).createdAt(value.createdAt()).revoked(value.revoked()).build());
    }
}
