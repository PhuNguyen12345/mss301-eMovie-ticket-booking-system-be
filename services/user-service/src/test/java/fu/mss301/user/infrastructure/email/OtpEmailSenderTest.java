package fu.mss301.user.infrastructure.email;

import fu.mss301.user.application.event.RegistrationOtpRequested;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import fu.mss301.user.domain.model.OtpDeliveryStatus;
import fu.mss301.user.infrastructure.persistence.repository.PendingRegistrationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class OtpEmailSenderTest {
    @Mock JavaMailSender mail;
    @Mock PendingRegistrationRepository repository;
    private OtpEmailSender sender;
    private final Instant now = Instant.parse("2026-10-05T07:00:00Z");
    private final RegistrationOtpRequested event = new RegistrationOtpRequested(
            "registration-id", "person@example.com", "012345", "hash", now.plusSeconds(300));

    @BeforeEach
    void setUp() {
        sender = new OtpEmailSender(mail, repository, Clock.fixed(now, ZoneOffset.UTC), "sender@example.com");
    }

    @Test
    void sendsOtpAndMarksSent() {
        when(repository.existsByIdAndOtpHashAndConfirmedAtIsNullAndExpiresAtAfter(anyString(), anyString(), any()))
                .thenReturn(true);
        sender.send(event);
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        assertArrayEquals(new String[]{"person@example.com"}, message.getValue().getTo());
        assertEquals("sender@example.com", message.getValue().getFrom());
        assertTrue(message.getValue().getText().contains("012345"));
        verify(repository).updateDeliveryStatus(eq("registration-id"), eq("hash"),
                eq(OtpDeliveryStatus.SENT), eq(OtpDeliveryStatus.PENDING), any());
    }

    @Test
    void recordsSmtpFailure() {
        when(repository.existsByIdAndOtpHashAndConfirmedAtIsNullAndExpiresAtAfter(anyString(), anyString(), any()))
                .thenReturn(true);
        doThrow(new MailSendException("SMTP failure")).when(mail).send(any(SimpleMailMessage.class));
        sender.send(event);
        verify(repository).updateDeliveryStatus(eq("registration-id"), eq("hash"),
                eq(OtpDeliveryStatus.FAILED), eq(OtpDeliveryStatus.PENDING), any());
    }

    @Test
    void doesNotSendAnObsoleteOtp() {
        sender.send(event);
        verifyNoInteractions(mail);
        verify(repository, never()).updateDeliveryStatus(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void marksExpiredQueuedOtpFailedWithoutSending() {
        sender.send(new RegistrationOtpRequested("registration-id", "person@example.com", "012345", "hash", now));
        verifyNoInteractions(mail);
        verify(repository).updateDeliveryStatus(eq("registration-id"), eq("hash"),
                eq(OtpDeliveryStatus.FAILED), eq(OtpDeliveryStatus.PENDING), any());
    }
}
