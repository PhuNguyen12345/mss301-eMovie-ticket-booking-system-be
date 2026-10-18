package fu.mss301.user.infrastructure.email;

import fu.mss301.user.application.event.RegistrationOtpRequested;

import fu.mss301.user.domain.model.OtpDeliveryStatus;
import fu.mss301.user.infrastructure.persistence.repository.PendingRegistrationRepository;
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
public class OtpEmailSender {
    private static final Logger log = LoggerFactory.getLogger(OtpEmailSender.class);
    private final JavaMailSender mailSender;
    private final PendingRegistrationRepository repository;
    private final Clock clock;
    private final String from;

    public OtpEmailSender(JavaMailSender mailSender, PendingRegistrationRepository repository,
                          Clock clock, @Value("${registration.otp.mail-from}") String from) {
        this.mailSender = mailSender;
        this.repository = repository;
        this.clock = clock;
        this.from = from;
    }

    public void send(RegistrationOtpRequested event) {
        try {
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            if (!event.expiresAt().isAfter(clock.instant())) {
                markFailed(event);
                return;
            }
            if (!repository.existsByIdAndOtpHashAndConfirmedAtIsNullAndExpiresAtAfter(
                    event.registrationId(), event.otpHash(), now)) {
                return;
            }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(event.email());
            message.setSubject("Xac nhan dang ky tai khoan E-Movie");
            message.setText("Ma OTP xac nhan dang ky cua ban: " + event.otp()
                    + "\nMa het han luc " + event.expiresAt()
                    + ".\nKhong chia se ma nay voi nguoi khac.");
            mailSender.send(message);
            updateStatus(event, OtpDeliveryStatus.SENT);
        } catch (RuntimeException ex) {
            // SMTP errors can contain email content. Log neither the exception nor the message.
            log.warn("OTP delivery failed for registration {}", event.registrationId());
            markFailed(event);
        }
    }

    public void markFailed(RegistrationOtpRequested event) {
        try {
            updateStatus(event, OtpDeliveryStatus.FAILED);
        } catch (RuntimeException ex) {
            log.error("Could not record OTP delivery failure for registration {}", event.registrationId());
        }
    }

    private void updateStatus(RegistrationOtpRequested event, OtpDeliveryStatus status) {
        repository.updateDeliveryStatus(event.registrationId(), event.otpHash(), status,
                OtpDeliveryStatus.PENDING, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }
}
