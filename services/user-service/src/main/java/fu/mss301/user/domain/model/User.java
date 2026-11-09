package fu.mss301.user.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@ToString
public class User {
    private Integer id;
    private String fullName;
    private String email;
    private String phone;
    @ToString.Exclude private String passwordHash;
    private LocalDate dateOfBirth;
    private String gender;
    private String cityProvince;
    private String district;
    private String address;
    private Integer preferredCinemaId;
    private String avatarUrl;
    private String status;
    private LocalDateTime emailVerifiedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @lombok.Singular private Set<UserRole> roles;

    public boolean isActiveAndVerified() {
        return "ACTIVE".equals(status) && emailVerifiedAt != null;
    }
    public void changePassword(String encodedPassword, LocalDateTime now) {
        if (!isActiveAndVerified())
            throw new fu.mss301.user.domain.exception.DomainException(
                    "Account is not active or email is not verified");
        if (encodedPassword == null || encodedPassword.isBlank())
            throw new fu.mss301.user.domain.exception.DomainException("Password hash is required");
        java.util.Objects.requireNonNull(now);
        passwordHash = encodedPassword;
        updatedAt = now;
    }
}
