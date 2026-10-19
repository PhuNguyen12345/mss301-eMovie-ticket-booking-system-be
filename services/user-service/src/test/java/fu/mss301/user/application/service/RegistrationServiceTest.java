package fu.mss301.user.application.service;

import fu.mss301.user.application.port.in.RegistrationService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import fu.mss301.common.exception.ApiException;
import fu.mss301.user.application.dto.RegisterRequest;
import fu.mss301.user.application.dto.ResendRegistrationOtpRequest;
import fu.mss301.user.domain.model.OtpDeliveryStatus;
import fu.mss301.user.domain.model.Registration;
import fu.mss301.user.infrastructure.notification.OtpGenerator;
import fu.mss301.user.application.event.RegistrationOtpRequested;
import fu.mss301.user.application.port.out.RegistrationStore;
import fu.mss301.user.application.port.out.UserStore;
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
import org.springframework.http.HttpStatus;
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
        ApiException ex = assertThrows(ApiException.class, () -> service.register(
                new RegisterRequest("Test", "person@example.com", null, "Secret123!")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verifyNoInteractions(generator, events, pending);
    }

    @Test
    void rejectsPasswordsOverBcryptByteLimit() {
        assertThrows(ApiException.class, () -> service.register(
                new RegisterRequest("Test", "person@example.com", null, "é".repeat(40))));
        verifyNoInteractions(generator, events, pending, users);
    }

    @Test
    void resendReplacesOtpAndResetsFailedAttemptsEvenAfterExpiry() {
        UUID id = UUID.fromString("16c98f29-3b38-480f-b969-72eaf6c1ae09");
        Registration registration = pendingRegistration(id);
        registration.setExpiresAt(LocalDateTime.ofInstant(now.minusSeconds(10), ZoneOffset.UTC));
        registration.setFailedAttempts(5);
        registration.setOtpDeliveryStatus(OtpDeliveryStatus.FAILED);
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
        registration.setNextOtpSendAt(LocalDateTime.ofInstant(now.plusSeconds(31), ZoneOffset.UTC));
        when(pending.findByIdForUpdate(id.toString())).thenReturn(Optional.of(registration));

        ApiException error = assertThrows(ApiException.class,
                () -> service.resendOtp(new ResendRegistrationOtpRequest(id)));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatus());
        assertEquals("31", error.getHeaders().getFirst("Retry-After"));
        assertEquals(0, registration.getResendCount());
        verifyNoInteractions(generator, events);
        verify(pending, never()).saveAndFlush(any());
    }

    @Test
    void fifthResendIsAllowedButSixthIsRejected() {
        UUID id = UUID.fromString("16c98f29-3b38-480f-b969-72eaf6c1ae09");
        Registration registration = pendingRegistration(id);
        registration.setResendCount(4);
        when(pending.findByIdForUpdate(id.toString())).thenReturn(Optional.of(registration));
        when(generator.generate()).thenReturn("654321");

        service.resendOtp(new ResendRegistrationOtpRequest(id));
        assertEquals(5, registration.getResendCount());

        ApiException error = assertThrows(ApiException.class,
                () -> service.resendOtp(new ResendRegistrationOtpRequest(id)));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatus());
        verify(generator, times(1)).generate();
        verify(events, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void confirmedRegistrationCannotResend() {
        UUID id = UUID.fromString("16c98f29-3b38-480f-b969-72eaf6c1ae09");
        Registration registration = pendingRegistration(id);
        registration.setConfirmedAt(LocalDateTime.ofInstant(now.minusSeconds(1), ZoneOffset.UTC));
        when(pending.findByIdForUpdate(id.toString())).thenReturn(Optional.of(registration));

        ApiException error = assertThrows(ApiException.class,
                () -> service.resendOtp(new ResendRegistrationOtpRequest(id)));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
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
