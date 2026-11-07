package fu.mss301.user.infrastructure.messaging.config;

import fu.mss301.user.application.port.out.OtpDeliveryPort;
import fu.mss301.user.application.projection.EventIngestion;
import fu.mss301.user.application.projection.OtpDeliveryProjectionHandler;
import fu.mss301.user.application.projection.ProjectionHandler;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OtpEventConfig {
    @Bean
    EventIngestion otpEventIngestion(List<OtpDeliveryPort<?>> deliveries) {
        List<ProjectionHandler<?>> handlers = new ArrayList<>();
        for (OtpDeliveryPort<?> delivery : deliveries) {
            handlers.add(handlerFor(delivery));
        }
        return new EventIngestion(handlers);
    }

    private static <E> ProjectionHandler<E> handlerFor(OtpDeliveryPort<E> delivery) {
        return new OtpDeliveryProjectionHandler<>(delivery);
    }
}
