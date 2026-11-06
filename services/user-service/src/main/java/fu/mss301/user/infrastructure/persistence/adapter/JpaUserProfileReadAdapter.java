package fu.mss301.user.infrastructure.persistence.adapter;

import fu.mss301.user.application.port.out.UserProfileReadPort;
import fu.mss301.user.application.projection.UserProfileView;
import fu.mss301.user.infrastructure.persistence.repository.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaUserProfileReadAdapter implements UserProfileReadPort {
    private final UserRepository repository;

    public JpaUserProfileReadAdapter(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<UserProfileView> findById(Integer userId) {
        return repository.findProfileById(userId).map(row -> new UserProfileView(
                row.getUserId(), row.getFullName(), row.getEmail(), row.getPhone(),
                row.getDateOfBirth(), row.getGender(), row.getCityProvince(), row.getDistrict(),
                row.getAddress(), row.getPreferredCinemaId(), row.getAvatarUrl(),
                row.getStatus(), row.getEmailVerifiedAt()));
    }
}
