package fu.mss301.user.infrastructure.persistence.adapter;

import fu.mss301.user.application.port.out.PasswordOtpStore;
import fu.mss301.user.domain.model.PasswordOtp;
import fu.mss301.user.domain.model.PasswordOtpPurpose;
import fu.mss301.user.infrastructure.persistence.entity.PasswordResetOtp;
import fu.mss301.user.infrastructure.persistence.repository.PasswordResetOtpRepository;
import fu.mss301.user.infrastructure.persistence.repository.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaPasswordOtpStore implements PasswordOtpStore {
    private final PasswordResetOtpRepository repository;
    private final UserRepository users;

    public JpaPasswordOtpStore(PasswordResetOtpRepository repository, UserRepository users) {
        this.repository = repository;
        this.users = users;
    }
    public Optional<PasswordOtp> findTopByUser_IdAndPurposeOrderByCreatedAtDescIdDesc(Integer userId, PasswordOtpPurpose purpose) {
        return repository.findTopByUser_IdAndPurposeOrderByCreatedAtDescIdDesc(userId, purpose)
                .map(JpaDomainMapper::passwordOtp);
    }
    public PasswordOtp saveAndFlush(PasswordOtp value) {
        PasswordResetOtp entity = repository.getReferenceById(value.getId());
        entity.setUsedAt(value.getUsedAt());
        entity.setFailedAttempts(value.getFailedAttempts());
        entity.setOtpDeliveryStatus(value.getOtpDeliveryStatus());
        return JpaDomainMapper.passwordOtp(repository.saveAndFlush(entity));
    }
    public PasswordOtp createAndFlush(NewPasswordOtp value) {
        PasswordResetOtp saved = repository.saveAndFlush(PasswordResetOtp.builder()
                .user(users.getReferenceById(value.user().getId()))
                .otpHash(value.otpHash()).purpose(value.purpose())
                .expiresAt(value.expiresAt()).createdAt(value.createdAt()).build());
        return JpaDomainMapper.passwordOtp(saved);
    }
}
