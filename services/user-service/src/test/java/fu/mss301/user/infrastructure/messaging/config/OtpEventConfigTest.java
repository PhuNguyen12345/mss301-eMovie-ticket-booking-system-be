package fu.mss301.user.infrastructure.messaging.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fu.mss301.user.application.port.out.OtpDeliveryPort;
import fu.mss301.user.application.projection.EventIngestion;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class OtpEventConfigTest {
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
