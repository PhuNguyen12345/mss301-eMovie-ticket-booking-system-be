package fu.mss301.user.application.usecase;

import fu.mss301.user.application.port.in.*;

import fu.mss301.user.application.usecase.*;
import fu.mss301.user.application.usecase.dto.UserProfileResponse;
import fu.mss301.user.application.usecase.dto.UserSummary;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import fu.mss301.user.application.port.out.UserProfileReadPort;

public class UserProfileUseCase implements UserProfileService {
    public static final int MAX_SUMMARIES = 100;
    private final UserProfileReadPort profiles;

    public UserProfileUseCase(UserProfileReadPort profiles) {
        this.profiles = profiles;
    }

    public UserProfileResponse me(Integer userId) {
        var profile = profiles.findById(userId)
                .orElseThrow(() -> new UseCaseException(StatusCode.NOT_FOUND, "UserAccount not found"));
        if (!profile.isActiveAndVerified()) {
            throw new UseCaseException(StatusCode.FORBIDDEN, "Account is not active or email is not verified");
        }
        return profile.toResponse();
    }

    public List<UserSummary> summaries(List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new UseCaseException(StatusCode.BAD_REQUEST, "At least one user id is required");
        }
        var ids = new LinkedHashSet<Integer>();
        for (Integer id : userIds) {
            if (id == null || id <= 0) throw new UseCaseException(StatusCode.BAD_REQUEST, "User ids must be positive");
            ids.add(id);
        }
        if (ids.size() > MAX_SUMMARIES) {
            throw new UseCaseException(StatusCode.BAD_REQUEST, "At most " + MAX_SUMMARIES + " users can be read at once");
        }
        Map<Integer, UserSummary> found = new HashMap<>();
        profiles.findAllById(ids).forEach(view -> found.put(view.userId(), view.toSummary()));
        return ids.stream().map(found::get).filter(Objects::nonNull).toList();
    }
}
