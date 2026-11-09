package fu.mss301.user.application.usecase;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import fu.mss301.user.application.usecase.dto.VerifyRegistrationRequest;
import fu.mss301.user.domain.model.Registration;
import fu.mss301.user.domain.model.UserRole;
import fu.mss301.user.domain.model.RoleName;
import fu.mss301.user.domain.model.User;
import fu.mss301.user.domain.repository.RegistrationStore;
import fu.mss301.user.domain.repository.RoleStore;
import fu.mss301.user.domain.repository.UserStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import fu.mss301.user.application.port.out.PasswordHasher;

class RegistrationVerificationServiceImplTest {
    private static final UUID ID = UUID.fromString("16c98f29-3b38-480f-b969-72eaf6c1ae09");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 7, 0);
    private final RegistrationStore pendingRepository = mock(RegistrationStore.class);
    private final UserStore users = mock(UserStore.class);
    private final RoleStore roles = mock(RoleStore.class);
    private final PasswordHasher encoder = mock(PasswordHasher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T07:00:00Z"), ZoneOffset.UTC);
    private final RegistrationVerificationUseCase service =
            new RegistrationVerificationUseCase(pendingRepository, users, roles, encoder, clock);
    private final VerifyRegistrationRequest request = new VerifyRegistrationRequest(ID, "012345");

    @Test
    void correctOtpCreatesVerifiedCustomerAndConfirmsRegistration() {
        Registration registration = pending();
        when(pendingRepository.findByIdForUpdate(ID.toString())).thenReturn(Optional.of(registration));
        when(encoder.matches("012345", "otp-hash")).thenReturn(true);
        when(roles.findByName(RoleName.CUSTOMER)).thenReturn(Optional.of(new UserRole(2, RoleName.CUSTOMER)));
        when(users.createAndFlush(any())).thenAnswer(invocation -> {
            UserStore.NewUser draft = invocation.getArgument(0);
            return User.builder().id(42).fullName(draft.fullName()).email(draft.email())
                    .phone(draft.phone()).passwordHash(draft.passwordHash()).status(draft.status())
                    .emailVerifiedAt(draft.emailVerifiedAt()).createdAt(draft.createdAt()).build();
        });

        var result = service.verify(request);

        assertEquals(42, result.userId());
        assertEquals("ACTIVE", result.status());
        assertEquals(NOW, registration.getConfirmedAt());
        var userCaptor = ArgumentCaptor.forClass(UserStore.NewUser.class);
        verify(users).createAndFlush(userCaptor.capture());
        UserStore.NewUser user = userCaptor.getValue();
        assertEquals("password-hash", user.passwordHash());
        assertEquals(NOW, user.emailVerifiedAt());
        assertEquals(NOW, user.createdAt());
        assertEquals(RoleName.CUSTOMER, user.roles().iterator().next().name());
        verify(pendingRepository).saveAndFlush(registration);
    }

    @Test
    void incorrectOtpPersistsAttemptAndReturnsErrorWithoutThrowing() {
        Registration registration = pending();
        when(pendingRepository.findByIdForUpdate(ID.toString())).thenReturn(Optional.of(registration));

        assertEquals(StatusCode.BAD_REQUEST, statusFor(request));
        assertEquals(1, registration.getFailedAttempts());
        assertEquals(NOW, registration.getUpdatedAt());
        verify(pendingRepository).saveAndFlush(registration);
        verifyNoInteractions(users, roles);
    }

    @Test
    void fifthIncorrectOtpLocksFurtherAttempts() {
        Registration registration = pending();
        registration = registration.toBuilder().failedAttempts(4).build();
        when(pendingRepository.findByIdForUpdate(ID.toString())).thenReturn(Optional.of(registration));

        assertEquals(StatusCode.TOO_MANY_REQUESTS, statusFor(request));
        assertEquals(5, registration.getFailedAttempts());
        verify(pendingRepository).saveAndFlush(registration);
        clearInvocations(pendingRepository, encoder);

        assertEquals(StatusCode.TOO_MANY_REQUESTS, statusFor(request));
        verify(pendingRepository, never()).saveAndFlush(any());
        verifyNoInteractions(encoder);
    }

    @Test
    void expiredOtpDoesNotCreateUser() {
        Registration registration = pending();
        registration = registration.toBuilder().expiresAt(NOW).build();
        when(pendingRepository.findByIdForUpdate(ID.toString())).thenReturn(Optional.of(registration));

        assertEquals(StatusCode.GONE, statusFor(request));
        verifyNoInteractions(users, roles, encoder);
    }

    @Test
    void alreadyConfirmedAndMissingRegistrationAreRejected() {
        assertEquals(StatusCode.NOT_FOUND, statusFor(request));
        Registration registration = pending();
        registration = registration.toBuilder().confirmedAt(NOW.minusMinutes(1)).build();
        when(pendingRepository.findByIdForUpdate(ID.toString())).thenReturn(Optional.of(registration));
        assertEquals(StatusCode.CONFLICT, statusFor(request));
        verifyNoInteractions(users, roles, encoder);
    }

    @Test
    void duplicateEmailDoesNotCreateAnotherUser() {
        when(pendingRepository.findByIdForUpdate(ID.toString())).thenReturn(Optional.of(pending()));
        when(encoder.matches("012345", "otp-hash")).thenReturn(true);
        when(users.existsByEmail("customer@example.com")).thenReturn(true);

        assertEquals(StatusCode.CONFLICT, statusFor(request));
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(roles);
    }

    private StatusCode statusFor(VerifyRegistrationRequest request) {
        return assertThrows(UseCaseException.class, () -> service.verify(request)).getStatus();
    }

    private Registration pending() {
        return Registration.builder().id(ID.toString()).fullName("Customer")
                .email("customer@example.com").phone("0912345678")
                .passwordHash("password-hash").otpHash("otp-hash")
                .expiresAt(NOW.plusMinutes(1)).build();
    }
}
