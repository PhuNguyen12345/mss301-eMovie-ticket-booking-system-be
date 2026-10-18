package fu.mss301.user.infrastructure.persistence.adapter;

import fu.mss301.user.application.port.out.RegistrationStore;
import fu.mss301.user.domain.model.Registration;
import fu.mss301.user.infrastructure.persistence.entity.PendingRegistration;
import fu.mss301.user.infrastructure.persistence.repository.PendingRegistrationRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaRegistrationStore implements RegistrationStore {
    private final PendingRegistrationRepository repository;

    public JpaRegistrationStore(PendingRegistrationRepository repository) { this.repository = repository; }
    public Optional<Registration> findByEmail(String email) { return repository.findByEmail(email).map(JpaDomainMapper::registration); }
    public Optional<Registration> findByIdForUpdate(String id) { return repository.findByIdForUpdate(id).map(JpaDomainMapper::registration); }
    public void delete(Registration value) { repository.delete(repository.getReferenceById(value.getId())); }
    public void flush() { repository.flush(); }
    public Registration saveAndFlush(Registration value) {
        PendingRegistration entity = repository.getReferenceById(value.getId());
        entity.setOtpHash(value.getOtpHash());
        entity.setExpiresAt(value.getExpiresAt());
        entity.setNextOtpSendAt(value.getNextOtpSendAt());
        entity.setFailedAttempts(value.getFailedAttempts());
        entity.setResendCount(value.getResendCount());
        entity.setOtpDeliveryStatus(value.getOtpDeliveryStatus());
        entity.setConfirmedAt(value.getConfirmedAt());
        entity.setUpdatedAt(value.getUpdatedAt());
        return JpaDomainMapper.registration(repository.saveAndFlush(entity));
    }

    public Registration createAndFlush(NewRegistration value) {
        PendingRegistration saved = repository.saveAndFlush(PendingRegistration.builder()
                .fullName(value.fullName()).email(value.email()).phone(value.phone())
                .passwordHash(value.passwordHash()).otpHash(value.otpHash())
                .expiresAt(value.expiresAt()).nextOtpSendAt(value.nextOtpSendAt())
                .createdAt(value.createdAt()).build());
        return JpaDomainMapper.registration(saved);
    }
}
