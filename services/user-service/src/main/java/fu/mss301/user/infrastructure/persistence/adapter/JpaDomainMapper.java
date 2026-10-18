package fu.mss301.user.infrastructure.persistence.adapter;

import fu.mss301.user.domain.model.PasswordOtp;
import fu.mss301.user.domain.model.RefreshCredential;
import fu.mss301.user.domain.model.Registration;
import fu.mss301.user.domain.model.UserRole;
import fu.mss301.user.infrastructure.persistence.entity.PasswordResetOtp;
import fu.mss301.user.infrastructure.persistence.entity.PendingRegistration;
import fu.mss301.user.infrastructure.persistence.entity.RefreshToken;
import fu.mss301.user.infrastructure.persistence.entity.Role;
import fu.mss301.user.infrastructure.persistence.entity.User;
import java.util.stream.Collectors;

/** Copies persistence state before it crosses into the application layer. */
final class JpaDomainMapper {
    private JpaDomainMapper() { }

    static fu.mss301.user.domain.model.User user(User entity, boolean includeRoles) {
        var snapshot = fu.mss301.user.domain.model.User.builder()
                .id(entity.getId()).fullName(entity.getFullName()).email(entity.getEmail())
                .phone(entity.getPhone()).passwordHash(entity.getPasswordHash())
                .dateOfBirth(entity.getDateOfBirth()).gender(entity.getGender())
                .cityProvince(entity.getCityProvince()).district(entity.getDistrict())
                .address(entity.getAddress()).preferredCinemaId(entity.getPreferredCinemaId())
                .avatarUrl(entity.getAvatarUrl()).status(entity.getStatus())
                .emailVerifiedAt(entity.getEmailVerifiedAt()).createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt()).build();
        if (includeRoles) {
            snapshot.setRoles(entity.getRoles().stream().map(JpaDomainMapper::role)
                    .collect(Collectors.toUnmodifiableSet()));
        }
        return snapshot;
    }

    static UserRole role(Role entity) {
        return new UserRole(entity.getId(), entity.getName());
    }

    static Registration registration(PendingRegistration entity) {
        return Registration.builder().id(entity.getId()).fullName(entity.getFullName())
                .email(entity.getEmail()).phone(entity.getPhone())
                .passwordHash(entity.getPasswordHash()).otpHash(entity.getOtpHash())
                .expiresAt(entity.getExpiresAt()).nextOtpSendAt(entity.getNextOtpSendAt())
                .failedAttempts(entity.getFailedAttempts()).resendCount(entity.getResendCount())
                .otpDeliveryStatus(entity.getOtpDeliveryStatus()).confirmedAt(entity.getConfirmedAt())
                .createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt())
                .version(entity.getVersion()).build();
    }

    static PasswordOtp passwordOtp(PasswordResetOtp entity) {
        return PasswordOtp.builder().id(entity.getId()).userId(entity.getUser().getId())
                .otpHash(entity.getOtpHash()).purpose(entity.getPurpose())
                .failedAttempts(entity.getFailedAttempts())
                .otpDeliveryStatus(entity.getOtpDeliveryStatus())
                .expiresAt(entity.getExpiresAt()).usedAt(entity.getUsedAt())
                .createdAt(entity.getCreatedAt()).version(entity.getVersion()).build();
    }

    static RefreshCredential refreshCredential(RefreshToken entity) {
        return RefreshCredential.builder().id(entity.getId())
                .user(user(entity.getUser(), true)).revoked(entity.getRevoked())
                .expiresAt(entity.getExpiresAt()).revokedAt(entity.getRevokedAt())
                .updatedAt(entity.getUpdatedAt()).build();
    }
}
