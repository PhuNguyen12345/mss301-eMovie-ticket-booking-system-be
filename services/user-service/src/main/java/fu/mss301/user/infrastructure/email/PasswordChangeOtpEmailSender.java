package fu.mss301.user.infrastructure.email;

import fu.mss301.user.application.event.PasswordChangeOtpRequested;

import fu.mss301.user.domain.model.OtpDeliveryStatus;
import fu.mss301.user.infrastructure.persistence.repository.PasswordResetOtpRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class PasswordChangeOtpEmailSender {
    private static final Logger log = LoggerFactory.getLogger(PasswordChangeOtpEmailSender.class);
    private final JavaMailSender mailSender;
    private final PasswordResetOtpRepository repository;
    private final Clock clock;
    private final String from;

    public PasswordChangeOtpEmailSender(JavaMailSender mailSender, PasswordResetOtpRepository repository,
                                        Clock clock, @Value("${password.otp.mail-from}") String from) {
        this.mailSender = mailSender;
        this.repository = repository;
        this.clock = clock;
        this.from = from;
    }

    public void send(PasswordChangeOtpRequested event) {
        try {
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            if (!event.expiresAt().isAfter(clock.instant())) {
                markFailed(event);
                return;
            }
            if (!repository.existsByIdAndOtpHashAndUsedAtIsNullAndExpiresAtAfter(
                    event.otpId(), event.otpHash(), now)) {
                return;
            }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(event.email());
            message.setSubject("Ma OTP doi mat khau E-Movie");
            message.setText("Ma OTP doi mat khau cua ban: " + event.otp()
                    + "\nMa het han luc " + event.expiresAt()
                    + ".\nKhong chia se ma nay voi nguoi khac.");
            mailSender.send(message);
            updateStatus(event, OtpDeliveryStatus.SENT);
        } catch (RuntimeException exception) {
            // SMTP exceptions may include message content; never log the exception.
            log.warn("Password change OTP delivery failed for request {}", event.otpId());
            markFailed(event);
        }
    }

    public void markFailed(PasswordChangeOtpRequested event) {
        try {
            updateStatus(event, OtpDeliveryStatus.FAILED);
        } catch (RuntimeException exception) {
            log.error("Could not record password change OTP delivery failure for request {}", event.otpId());
        }
    }

    private void updateStatus(PasswordChangeOtpRequested event, OtpDeliveryStatus status) {
        repository.updateDeliveryStatus(event.otpId(), event.otpHash(), status, OtpDeliveryStatus.PENDING);
    }
}
