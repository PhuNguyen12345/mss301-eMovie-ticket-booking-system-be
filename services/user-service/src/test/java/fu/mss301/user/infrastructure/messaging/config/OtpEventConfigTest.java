package fu.mss301.user.infrastructure.messaging.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fu.mss301.user.application.port.out.OtpDeliveryPort;
import fu.mss301.user.application.projection.EventIngestion;
import fu.mss301.user.domain.event.RegistrationOtpRequested;
import fu.mss301.user.domain.event.PasswordChangeOtpRequested;
import fu.mss301.user.domain.event.PasswordResetOtpRequested;
import fu.mss301.user.infrastructure.email.OtpEmailSender;
import fu.mss301.user.infrastructure.email.PasswordChangeOtpEmailSender;
import fu.mss301.user.infrastructure.email.PasswordResetOtpEmailSender;
import fu.mss301.user.infrastructure.persistence.repository.PendingRegistrationRepository;
import fu.mss301.user.infrastructure.persistence.repository.PasswordResetOtpRepository;
import org.springframework.mail.javamail.JavaMailSender;
import java.time.Clock;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class OtpEventConfigTest {
    @Test
    void discoversAllConcreteEmailAdaptersAsDeliveryPorts() {
        var mail = mock(JavaMailSender.class);
        var registrations = mock(PendingRegistrationRepository.class);
        var passwords = mock(PasswordResetOtpRepository.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(OtpEventConfig.class);
            context.registerBean(OtpEmailSender.class,
                    () -> new OtpEmailSender(mail, registrations, Clock.systemUTC(), "test@example.test"));
            context.registerBean(PasswordChangeOtpEmailSender.class,
                    () -> new PasswordChangeOtpEmailSender(mail, passwords, Clock.systemUTC(), "test@example.test"));
            context.registerBean(PasswordResetOtpEmailSender.class,
                    () -> new PasswordResetOtpEmailSender(mail, passwords, Clock.systemUTC(), "test@example.test"));
            context.refresh();

            var types = context.getBeansOfType(OtpDeliveryPort.class).values().stream()
                    .map(OtpDeliveryPort::eventType).collect(Collectors.toSet());
            assertEquals(Set.of(RegistrationOtpRequested.class, PasswordChangeOtpRequested.class,
                    PasswordResetOtpRequested.class), types);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void registersDeliveryAdapterByEventType() {
        OtpDeliveryPort<String> delivery = mock(OtpDeliveryPort.class);
        when(delivery.eventType()).thenReturn(String.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(OtpEventConfig.class);
            context.registerBean("delivery", OtpDeliveryPort.class, () -> delivery);
            context.refresh();

            context.getBean(EventIngestion.class).process("otp-event");
            verify(delivery).send("otp-event");
        }
    }
}
