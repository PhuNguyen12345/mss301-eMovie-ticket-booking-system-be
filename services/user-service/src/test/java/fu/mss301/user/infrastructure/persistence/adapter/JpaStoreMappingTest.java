package fu.mss301.user.infrastructure.persistence.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fu.mss301.user.application.port.out.PasswordOtpStore;
import fu.mss301.user.application.port.out.RefreshTokenStore;
import fu.mss301.user.application.port.out.RegistrationStore;
import fu.mss301.user.application.port.out.UserStore;
import fu.mss301.user.domain.model.PasswordOtpPurpose;
import fu.mss301.user.domain.model.PasswordOtp;
import fu.mss301.user.domain.model.RefreshCredential;
import fu.mss301.user.domain.model.Registration;
import fu.mss301.user.domain.model.RoleName;
import fu.mss301.user.domain.model.UserRole;
import fu.mss301.user.infrastructure.persistence.entity.PasswordResetOtp;
import fu.mss301.user.infrastructure.persistence.entity.PendingRegistration;
import fu.mss301.user.infrastructure.persistence.entity.RefreshToken;
import fu.mss301.user.infrastructure.persistence.entity.Role;
import fu.mss301.user.infrastructure.persistence.entity.User;
import fu.mss301.user.infrastructure.persistence.repository.PasswordResetOtpRepository;
import fu.mss301.user.infrastructure.persistence.repository.PendingRegistrationRepository;
import fu.mss301.user.infrastructure.persistence.repository.RefreshTokenRepository;
import fu.mss301.user.infrastructure.persistence.repository.RoleRepository;
import fu.mss301.user.infrastructure.persistence.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JpaStoreMappingTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 7, 0);

    @Test
    void registrationDraftKeepsHashesAndOtpSchedule() {
        var repository = mock(PendingRegistrationRepository.class);
        when(repository.saveAndFlush(any(PendingRegistration.class))).thenAnswer(call -> call.getArgument(0));
        var draft = new RegistrationStore.NewRegistration("Customer", "customer@example.com", null,
                "password-hash", "otp-hash", NOW.plusMinutes(5), NOW.plusMinutes(1), NOW);
        var saved = new JpaRegistrationStore(repository).createAndFlush(draft);

        assertEquals("password-hash", saved.getPasswordHash());
        assertEquals("otp-hash", saved.getOtpHash());
        assertEquals(NOW.plusMinutes(5), saved.getExpiresAt());
        assertEquals(NOW.plusMinutes(1), saved.getNextOtpSendAt());
        assertInstanceOf(Registration.class, saved);
        assertFalse(draft.toString().contains("otp-hash"));
    }

    @Test
    void registeredUserKeepsRoleAndVerificationTime() {
        var repository = mock(UserRepository.class);
        var roleRepository = mock(RoleRepository.class);
        when(repository.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));
        var role = Role.builder().id(2).name(RoleName.CUSTOMER).build();
        when(roleRepository.getReferenceById(2)).thenReturn(role);
        var draft = new UserStore.NewUser("Customer", "customer@example.com", null,
                "password-hash", "ACTIVE", NOW, NOW, Set.of(new UserRole(2, RoleName.CUSTOMER)));
        var saved = new JpaUserStore(repository, roleRepository).createAndFlush(draft);

        assertEquals("ACTIVE", saved.getStatus());
        assertEquals(NOW, saved.getEmailVerifiedAt());
        assertEquals(RoleName.CUSTOMER, saved.getRoles().iterator().next().name());
        assertInstanceOf(fu.mss301.user.domain.model.User.class, saved);
    }

    @Test
    void passwordOtpKeepsPurposeAndUserAssociation() {
        var repository = mock(PasswordResetOtpRepository.class);
        var users = mock(UserRepository.class);
        when(repository.saveAndFlush(any(PasswordResetOtp.class))).thenAnswer(call -> call.getArgument(0));
        var user = User.builder().id(7).build();
        when(users.getReferenceById(7)).thenReturn(user);
        var draft = new PasswordOtpStore.NewPasswordOtp(fu.mss301.user.domain.model.User.builder().id(7).build(), "otp-hash",
                PasswordOtpPurpose.PASSWORD_RESET, NOW.plusMinutes(5), NOW);
        var saved = new JpaPasswordOtpStore(repository, users).createAndFlush(draft);

        assertEquals(PasswordOtpPurpose.PASSWORD_RESET, saved.getPurpose());
        assertEquals(7, saved.getUserId());
        assertInstanceOf(PasswordOtp.class, saved);
    }

    @Test
    void refreshTokenKeepsHashAndExpiry() {
        var repository = mock(RefreshTokenRepository.class);
        var users = mock(UserRepository.class);
        var user = User.builder().id(7).build();
        when(users.getReferenceById(7)).thenReturn(user);
        var draft = new RefreshTokenStore.NewRefreshToken(fu.mss301.user.domain.model.User.builder().id(7).build(), "token-hash", NOW.plusDays(30), NOW, false);
        new JpaRefreshTokenStore(repository, users).saveNew(draft);

        var saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertSame(user, saved.getValue().getUser());
        assertEquals("token-hash", saved.getValue().getTokenHash());
        assertEquals(NOW.plusDays(30), saved.getValue().getExpiresAt());
        assertFalse(draft.toString().contains("token-hash"));
    }

    @Test
    void updatingRegistrationCopiesSnapshotIntoManagedEntity() {
        var repository = mock(PendingRegistrationRepository.class);
        var entity = PendingRegistration.builder().id("registration-id").failedAttempts(0).build();
        when(repository.getReferenceById("registration-id")).thenReturn(entity);
        when(repository.saveAndFlush(entity)).thenReturn(entity);
        var snapshot = Registration.builder().id("registration-id")
                .failedAttempts(2).otpHash("new-otp-hash").updatedAt(NOW).build();

        new JpaRegistrationStore(repository).saveAndFlush(snapshot);

        assertEquals(2, entity.getFailedAttempts());
        assertEquals("new-otp-hash", entity.getOtpHash());
        assertEquals(NOW, entity.getUpdatedAt());
        verify(repository).saveAndFlush(entity);
    }

    @Test
    void updatingPasswordOtpCopiesSnapshotIntoManagedEntity() {
        var repository = mock(PasswordResetOtpRepository.class);
        var users = mock(UserRepository.class);
        var user = User.builder().id(7).build();
        var entity = PasswordResetOtp.builder().id(12).user(user).failedAttempts(0).build();
        when(repository.getReferenceById(12)).thenReturn(entity);
        when(repository.saveAndFlush(entity)).thenReturn(entity);
        var snapshot = PasswordOtp.builder().id(12).userId(7)
                .failedAttempts(3).usedAt(NOW).build();

        new JpaPasswordOtpStore(repository, users).saveAndFlush(snapshot);

        assertEquals(3, entity.getFailedAttempts());
        assertEquals(NOW, entity.getUsedAt());
        verify(repository).saveAndFlush(entity);
    }

    @Test
    void revokingRefreshTokenCopiesSnapshotIntoManagedEntity() {
        var repository = mock(RefreshTokenRepository.class);
        var users = mock(UserRepository.class);
        var entity = RefreshToken.builder().id(23).revoked(false).build();
        when(repository.getReferenceById(23)).thenReturn(entity);
        var snapshot = RefreshCredential.builder().id(23)
                .revoked(true).revokedAt(NOW).updatedAt(NOW).build();

        new JpaRefreshTokenStore(repository, users).update(snapshot);

        assertEquals(true, entity.getRevoked());
        assertEquals(NOW, entity.getRevokedAt());
        assertEquals(NOW, entity.getUpdatedAt());
    }
}
