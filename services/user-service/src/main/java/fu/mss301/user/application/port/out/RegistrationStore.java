package fu.mss301.user.application.port.out;

import fu.mss301.user.domain.model.Registration;
import java.time.LocalDateTime;
import java.util.Optional;

public interface RegistrationStore {
    Optional<Registration> findByEmail(String email);
    Optional<Registration> findByIdForUpdate(String id);
    void delete(Registration registration);
    void flush();
    Registration saveAndFlush(Registration registration);
    Registration createAndFlush(NewRegistration registration);

    record NewRegistration(String fullName, String email, String phone, String passwordHash,
                           String otpHash, LocalDateTime expiresAt, LocalDateTime nextOtpSendAt,
                           LocalDateTime createdAt) {
        @Override public String toString() { return "NewRegistration[email=" + email + ", passwordHash=[REDACTED], otpHash=[REDACTED]]"; }
    }
}
