package fu.mss301.user.application.usecase;

import fu.mss301.user.application.port.in.RegistrationService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import fu.mss301.user.application.usecase.dto.RegisterRequest;
import fu.mss301.user.application.usecase.dto.ResendRegistrationOtpRequest;
import fu.mss301.user.domain.model.OtpDeliveryStatus;
import fu.mss301.user.domain.model.Registration;
import fu.mss301.user.infrastructure.security.OtpGenerator;
import fu.mss301.user.domain.event.RegistrationOtpRequested;
import fu.mss301.user.domain.repository.RegistrationStore;
import fu.mss301.user.domain.repository.UserStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import fu.mss301.user.application.port.out.EventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import fu.mss301.user.infrastructure.security.SpringPasswordHasher;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {
    @Mock UserStore users;
    @Mock RegistrationStore pending;
    @Mock OtpGenerator generator;
    @Mock EventPublisher events;
    private RegistrationService service;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final Instant now = Instant.parse("2026-10-05T07:00:00Z");

    @BeforeEach
    void setUp() {
        service = new RegistrationUseCase(users, pending, new SpringPasswordHasher(encoder), generator, events,
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(5), Duration.ofSeconds(60), 5);
    }

    @Test
    void storesOnlyHashesAndRequestsMailWithoutCreatingUser() {
        when(generator.generate()).thenReturn("012345");
        when(pending.createAndFlush(any())).thenAnswer(invocation -> {
            RegistrationStore.NewRegistration draft = invocation.getArgument(0);
            return Registration.builder().id("registration-id").fullName(draft.fullName())
                    .email(draft.email()).phone(draft.phone()).passwordHash(draft.passwordHash())
                    .otpHash(draft.otpHash()).expiresAt(draft.expiresAt())
                    .nextOtpSendAt(draft.nextOtpSendAt()).createdAt(draft.createdAt()).build();
        });
        var response = service.register(new RegisterRequest(" Test Person ", "Person@Example.com", "", "Secret123!"));
        ArgumentCaptor<RegistrationStore.NewRegistration> saved = ArgumentCaptor.forClass(RegistrationStore.NewRegistration.class);
        verify(pending).createAndFlush(saved.capture());
        assertEquals("person@example.com", saved.getValue().email());
        assertNull(saved.getValue().phone());
        assertTrue(encoder.matches("012345", saved.getValue().otpHash()));
        assertTrue(encoder.matches("Secret123!", saved.getValue().passwordHash()));
        assertFalse(saved.getValue().toString().contains(saved.getValue().otpHash()));
        assertEquals(now.plusSeconds(300), response.expiresAt());
        assertEquals(now.plusSeconds(60), response.resendAvailableAt());
        assertEquals(OtpDeliveryStatus.PENDING, response.otpDeliveryStatus());
        verify(users, never()).saveAndFlush(any());
        ArgumentCaptor<RegistrationOtpRequested> event = ArgumentCaptor.forClass(RegistrationOtpRequested.class);
        verify(events).publishEvent(event.capture());
        assertEquals("012345", event.getValue().otp());
        assertFalse(event.getValue().toString().contains("012345"));
    }

    @Test
    void rejectsExistingUserWithoutIssuingOtp() {
        when(users.existsByEmail("person@example.com")).thenReturn(true);
        UseCaseException ex = assertThrows(UseCaseException.class, () -> service.register(
                new RegisterRequest("Test", "person@example.com", null, "Secret123!")));
        assertEquals(StatusCode.CONFLICT, ex.getStatus());
        verifyNoInteractions(generator, events, pending);
    }

    @Test
    void rejectsPasswordsOverBcryptByteLimit() {
        assertThrows(UseCaseException.class, () -> service.register(
                new RegisterRequest("Test", "person@example.com", null, "é".repeat(40))));
        verifyNoInteractions(generator, events, pending, users);
    }

    @Test
    void resendReplacesOtpAndResetsFailedAttemptsEvenAfterExpiry() {
        UUID id = UUID.fromString("16c98f29-3b38-480f-b969-72eaf6c1ae09");
        Registration registration = pendingRegistration(id);
        registration = registration.toBuilder().expiresAt(LocalDateTime.ofInstant(now.minusSeconds(10), ZoneOffset.UTC)).build();
        registration = registration.toBuilder().failedAttempts(5).build();
        registration = registration.toBuilder().otpDeliveryStatus(OtpDeliveryStatus.FAILED).build();
        when(pending.findByIdForUpdate(id.toString())).thenReturn(Optional.of(registration));
        when(generator.generate()).thenReturn("654321");

        var response = service.resendOtp(new ResendRegistrationOtpRequest(id));

        assertEquals(id.toString(), response.registrationId());
        assertEquals(now.plusSeconds(300), response.expiresAt());
        assertEquals(now.plusSeconds(60), response.resendAvailableAt());
        assertEquals(OtpDeliveryStatus.PENDING, response.otpDeliveryStatus());
        assertTrue(encoder.matches("654321", registration.getOtpHash()));
        assertFalse(encoder.matches("012345", registration.getOtpHash()));
        assertEquals(0, registration.getFailedAttempts());
        assertEquals(1, registration.getResendCount());
        assertEquals(OtpDeliveryStatus.PENDING, registration.getOtpDeliveryStatus());
        verify(pending).saveAndFlush(registration);
        ArgumentCaptor<RegistrationOtpRequested> event = ArgumentCaptor.forClass(RegistrationOtpRequested.class);
        verify(events).publishEvent(event.capture());
        assertEquals("654321", event.getValue().otp());
        assertEquals(registration.getOtpHash(), event.getValue().otpHash());
    }

    @Test
    void cooldownReturnsRetryAfterWithoutChangingRegistration() {
        UUID id = UUID.fromString("16c98f29-3b38-480f-b969-72eaf6c1ae09");
        Registration registration = pendingRegistration(id);
        registration = registration.toBuilder().nextOtpSendAt(LocalDateTime.ofInstant(now.plusSeconds(31), ZoneOffset.UTC)).build();
        when(pending.findByIdForUpdate(id.toString())).thenReturn(Optional.of(registration));

        UseCaseException error = assertThrows(UseCaseException.class,
                () -> service.resendOtp(new ResendRegistrationOtpRequest(id)));

        assertEquals(StatusCode.TOO_MANY_REQUESTS, error.getStatus());
        assertEquals(31L, error.retryAfterSeconds());
        assertEquals(0, registration.getResendCount());
        verifyNoInteractions(generator, events);
        verify(pending, never()).saveAndFlush(any());
    }

    @Test
    void fifthResendIsAllowedButSixthIsRejected() {
        UUID id = UUID.fromString("16c98f29-3b38-480f-b969-72eaf6c1ae09");
        Registration registration = pendingRegistration(id);
        registration = registration.toBuilder().resendCount(4).build();
        when(pending.findByIdForUpdate(id.toString())).thenReturn(Optional.of(registration));
        when(generator.generate()).thenReturn("654321");

        service.resendOtp(new ResendRegistrationOtpRequest(id));
        assertEquals(5, registration.getResendCount());

        UseCaseException error = assertThrows(UseCaseException.class,
                () -> service.resendOtp(new ResendRegistrationOtpRequest(id)));
        assertEquals(StatusCode.TOO_MANY_REQUESTS, error.getStatus());
        verify(generator, times(1)).generate();
        verify(events, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void confirmedRegistrationCannotResend() {
        UUID id = UUID.fromString("16c98f29-3b38-480f-b969-72eaf6c1ae09");
        Registration registration = pendingRegistration(id);
        registration = registration.toBuilder().confirmedAt(LocalDateTime.ofInstant(now.minusSeconds(1), ZoneOffset.UTC)).build();
        when(pending.findByIdForUpdate(id.toString())).thenReturn(Optional.of(registration));

        UseCaseException error = assertThrows(UseCaseException.class,
                () -> service.resendOtp(new ResendRegistrationOtpRequest(id)));
        assertEquals(StatusCode.CONFLICT, error.getStatus());
        verifyNoInteractions(generator, events);
    }

    private Registration pendingRegistration(UUID id) {
        return Registration.builder().id(id.toString()).fullName("Test Person")
                .email("person@example.com").passwordHash("password-hash")
                .otpHash(encoder.encode("012345"))
                .expiresAt(LocalDateTime.ofInstant(now.plusSeconds(300), ZoneOffset.UTC))
                .nextOtpSendAt(LocalDateTime.ofInstant(now.minusSeconds(1), ZoneOffset.UTC))
                .createdAt(LocalDateTime.ofInstant(now.minusSeconds(61), ZoneOffset.UTC)).build();
    }
}
