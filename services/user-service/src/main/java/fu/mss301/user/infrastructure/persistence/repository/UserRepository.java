package fu.mss301.user.infrastructure.persistence.repository;

import fu.mss301.user.infrastructure.persistence.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.time.LocalDate;
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
    @Query("""
            select u.id as userId, u.fullName as fullName, u.email as email, u.phone as phone,
                   u.dateOfBirth as dateOfBirth, u.gender as gender,
                   u.cityProvince as cityProvince, u.district as district,
                   u.address as address, u.preferredCinemaId as preferredCinemaId,
                   u.avatarUrl as avatarUrl, u.status as status,
                   u.emailVerifiedAt as emailVerifiedAt
            from User u where u.id = :id
            """)
    Optional<ProfileRow> findProfileById(@Param("id") Integer id);

    @Query("""
            select u.id as userId, u.fullName as fullName, u.email as email, u.phone as phone,
                   u.dateOfBirth as dateOfBirth, u.gender as gender,
                   u.cityProvince as cityProvince, u.district as district,
                   u.address as address, u.preferredCinemaId as preferredCinemaId,
                   u.avatarUrl as avatarUrl, u.status as status,
                   u.emailVerifiedAt as emailVerifiedAt
            from User u where u.id in :ids
            """)
    List<ProfileRow> findProfilesByIds(@Param("ids") Collection<Integer> ids);

    interface ProfileRow {
        Integer getUserId();
        String getFullName();
        String getEmail();
        String getPhone();
        LocalDate getDateOfBirth();
        String getGender();
        String getCityProvince();
        String getDistrict();
        String getAddress();
        Integer getPreferredCinemaId();
        String getAvatarUrl();
        String getStatus();
        LocalDateTime getEmailVerifiedAt();
    }

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
