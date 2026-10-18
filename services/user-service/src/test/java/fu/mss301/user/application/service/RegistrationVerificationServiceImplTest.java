package fu.mss301.user.application.service;

import fu.mss301.common.exception.ApiException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import fu.mss301.user.application.dto.VerifyRegistrationRequest;
import fu.mss301.user.domain.model.Registration;
import fu.mss301.user.domain.model.UserRole;
import fu.mss301.user.domain.model.RoleName;
import fu.mss301.user.domain.model.User;
import fu.mss301.user.application.port.out.RegistrationStore;
import fu.mss301.user.application.port.out.RoleStore;
import fu.mss301.user.application.port.out.UserStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
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

        assertEquals(HttpStatus.BAD_REQUEST, statusFor(request));
        assertEquals(1, registration.getFailedAttempts());
        assertEquals(NOW, registration.getUpdatedAt());
        verify(pendingRepository).saveAndFlush(registration);
        verifyNoInteractions(users, roles);
    }

    @Test
    void fifthIncorrectOtpLocksFurtherAttempts() {
        Registration registration = pending();
        registration.setFailedAttempts(4);
        when(pendingRepository.findByIdForUpdate(ID.toString())).thenReturn(Optional.of(registration));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, statusFor(request));
        assertEquals(5, registration.getFailedAttempts());
        verify(pendingRepository).saveAndFlush(registration);
        clearInvocations(pendingRepository, encoder);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, statusFor(request));
        verify(pendingRepository, never()).saveAndFlush(any());
        verifyNoInteractions(encoder);
    }

    @Test
    void expiredOtpDoesNotCreateUser() {
        Registration registration = pending();
        registration.setExpiresAt(NOW);
        when(pendingRepository.findByIdForUpdate(ID.toString())).thenReturn(Optional.of(registration));

        assertEquals(HttpStatus.GONE, statusFor(request));
        verifyNoInteractions(users, roles, encoder);
    }

    @Test
    void alreadyConfirmedAndMissingRegistrationAreRejected() {
        assertEquals(HttpStatus.NOT_FOUND, statusFor(request));
        Registration registration = pending();
        registration.setConfirmedAt(NOW.minusMinutes(1));
        when(pendingRepository.findByIdForUpdate(ID.toString())).thenReturn(Optional.of(registration));
        assertEquals(HttpStatus.CONFLICT, statusFor(request));
        verifyNoInteractions(users, roles, encoder);
    }

    @Test
    void duplicateEmailDoesNotCreateAnotherUser() {
        when(pendingRepository.findByIdForUpdate(ID.toString())).thenReturn(Optional.of(pending()));
        when(encoder.matches("012345", "otp-hash")).thenReturn(true);
        when(users.existsByEmail("customer@example.com")).thenReturn(true);

        assertEquals(HttpStatus.CONFLICT, statusFor(request));
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(roles);
    }

    private HttpStatus statusFor(VerifyRegistrationRequest request) {
        return assertThrows(ApiException.class, () -> service.verify(request)).getStatus();
    }

    private Registration pending() {
        return Registration.builder().id(ID.toString()).fullName("Customer")
                .email("customer@example.com").phone("0912345678")
                .passwordHash("password-hash").otpHash("otp-hash")
                .expiresAt(NOW.plusMinutes(1)).build();
    }
}
