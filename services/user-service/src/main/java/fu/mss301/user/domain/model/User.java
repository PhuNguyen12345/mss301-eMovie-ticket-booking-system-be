package fu.mss301.user.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    @Builder.Default private Set<UserRole> roles = new HashSet<>();

    public boolean isActiveAndVerified() {
        return "ACTIVE".equals(status) && emailVerifiedAt != null;
    }
}
