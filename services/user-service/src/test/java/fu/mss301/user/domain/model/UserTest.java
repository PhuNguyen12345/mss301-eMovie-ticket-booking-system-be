package fu.mss301.user.domain.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class UserTest {
    @Test
    void activeAccountRequiresEmailVerification() {
        var user = User.builder().status("ACTIVE").build();
        assertFalse(user.isActiveAndVerified());

        user = user.toBuilder().emailVerifiedAt(LocalDateTime.of(2026, 10, 18, 12, 0)).build();
        assertTrue(user.isActiveAndVerified());

        user = user.toBuilder().status("SUSPENDED").build();
        assertFalse(user.isActiveAndVerified());
    }
}
