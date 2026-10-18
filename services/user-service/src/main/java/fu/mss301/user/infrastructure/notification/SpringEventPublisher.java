package fu.mss301.user.infrastructure.notification;

import fu.mss301.user.application.port.out.EventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SpringEventPublisher implements EventPublisher {
    private final ApplicationEventPublisher publisher;

    public SpringEventPublisher(ApplicationEventPublisher publisher) { this.publisher = publisher; }
    public void publishEvent(Object event) { publisher.publishEvent(event); }
}
