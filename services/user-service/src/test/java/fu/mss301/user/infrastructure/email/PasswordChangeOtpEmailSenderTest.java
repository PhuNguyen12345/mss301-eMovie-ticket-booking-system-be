package fu.mss301.user.infrastructure.email;

import fu.mss301.user.application.event.PasswordChangeOtpRequested;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fu.mss301.user.domain.model.OtpDeliveryStatus;
import fu.mss301.user.infrastructure.persistence.repository.PasswordResetOtpRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class PasswordChangeOtpEmailSenderTest {
    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final PasswordResetOtpRepository otps = mock(PasswordResetOtpRepository.class);
    private final Instant now = Instant.parse("2026-10-14T07:00:00Z");
    private final PasswordChangeOtpEmailSender sender = new PasswordChangeOtpEmailSender(
            mail, otps, Clock.fixed(now, ZoneOffset.UTC), "sender@example.com");
    private final PasswordChangeOtpRequested event = new PasswordChangeOtpRequested(
            12, "person@example.com", "012345", "hash", now.plusSeconds(300));

    @Test
    void sendsOnlyCurrentOtpAndMarksSent() {
        when(otps.existsByIdAndOtpHashAndUsedAtIsNullAndExpiresAtAfter(eq(12), eq("hash"), any()))
                .thenReturn(true);
        sender.send(event);
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        assertTrue(message.getValue().getText().contains("012345"));
        verify(otps).updateDeliveryStatus(12, "hash", OtpDeliveryStatus.SENT, OtpDeliveryStatus.PENDING);
    }

    @Test
    void obsoleteOtpIsNotSent() {
        sender.send(event);
        verifyNoInteractions(mail);
        verify(otps, never()).updateDeliveryStatus(any(), any(), any(), any());
    }

    @Test
    void smtpFailureMarksOtpFailedWithoutThrowing() {
        when(otps.existsByIdAndOtpHashAndUsedAtIsNullAndExpiresAtAfter(eq(12), eq("hash"), any()))
                .thenReturn(true);
        doThrow(new MailSendException("SMTP failure")).when(mail).send(any(SimpleMailMessage.class));
        sender.send(event);
        verify(otps).updateDeliveryStatus(12, "hash", OtpDeliveryStatus.FAILED, OtpDeliveryStatus.PENDING);
    }
}
