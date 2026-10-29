package fu.mss301.booking.presentation.security;

import java.util.Set;

/** Caller identity forwarded by api-gateway after JWT validation (X-User-Id / X-User-Roles). */
public record AuthenticatedUser(Integer id, Set<String> roles) {
    public boolean isAdmin() {
        return roles.contains("ADMIN");
    }
}
