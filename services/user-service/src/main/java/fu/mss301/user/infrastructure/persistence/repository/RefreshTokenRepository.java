package fu.mss301.user.infrastructure.persistence.repository;

import fu.mss301.user.infrastructure.persistence.entity.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update RefreshToken t set t.revoked = true, t.revokedAt = :now, t.updatedAt = :now
            where t.user.id = :userId and t.revoked = false
            """)
    int revokeActiveByUserId(@Param("userId") Integer userId, @Param("now") LocalDateTime now);
}
