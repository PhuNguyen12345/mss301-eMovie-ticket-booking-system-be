package fu.mss301.user.application.port.out;

import fu.mss301.user.domain.model.User;
import fu.mss301.user.domain.model.UserRole;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

public interface UserStore {
    Optional<User> findById(Integer id);
    Optional<User> findByEmail(String email);
    Optional<User> findByIdForUpdate(Integer id);
    Optional<Integer> findIdByEmail(String email);
    User getReferenceById(Integer id);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    int updateLastLoginAt(Integer id, LocalDateTime at);
    User saveAndFlush(User user);
    User createAndFlush(NewUser user);

    record NewUser(String fullName, String email, String phone, String passwordHash,
                   String status, LocalDateTime emailVerifiedAt, LocalDateTime createdAt,
                   Set<UserRole> roles) {
        @Override public String toString() { return "NewUser[email=" + email + ", passwordHash=[REDACTED]]"; }
    }
}
