package fu.mss301.user.infrastructure.persistence.adapter;

import fu.mss301.user.application.port.out.UserProfileReadPort;
import fu.mss301.user.application.projection.UserProfileView;
import fu.mss301.user.infrastructure.persistence.repository.UserRepository;
import java.util.Collection;
import java.util.List;
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
        return repository.findProfileById(userId).map(JpaUserProfileReadAdapter::toView);
    }

    @Override
    public List<UserProfileView> findAllById(Collection<Integer> userIds) {
        return repository.findProfilesByIds(userIds).stream().map(JpaUserProfileReadAdapter::toView).toList();
    }

    private static UserProfileView toView(UserRepository.ProfileRow row) {
        return new UserProfileView(
                row.getUserId(), row.getFullName(), row.getEmail(), row.getPhone(),
                row.getDateOfBirth(), row.getGender(), row.getCityProvince(), row.getDistrict(),
                row.getAddress(), row.getPreferredCinemaId(), row.getAvatarUrl(),
                row.getStatus(), row.getEmailVerifiedAt());
    }
}
