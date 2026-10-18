package fu.mss301.user.infrastructure.persistence.repository;

import fu.mss301.user.infrastructure.persistence.entity.User;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    @Query("select u.id from User u where u.email = :email")
    Optional<Integer> findIdByEmail(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Integer id);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByEmail(String email);

    @Modifying
    @Query("update User u set u.lastLoginAt = :loginAt where u.id = :id and u.status = 'ACTIVE' and u.emailVerifiedAt is not null")
    int updateLastLoginAt(@Param("id") Integer id, @Param("loginAt") LocalDateTime loginAt);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);
}
