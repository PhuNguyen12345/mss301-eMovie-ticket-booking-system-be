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

        user.setEmailVerifiedAt(LocalDateTime.of(2026, 10, 18, 12, 0));
        assertTrue(user.isActiveAndVerified());

        user.setStatus("SUSPENDED");
        assertFalse(user.isActiveAndVerified());
    }
}
