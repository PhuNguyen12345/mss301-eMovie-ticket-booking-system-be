package fu.mss301.booking.infrastructure.event;

import fu.mss301.booking.application.port.DomainEvents;
import fu.mss301.booking.domain.shared.DomainEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Component
public class SpringDomainEvents implements DomainEvents {
    private final ApplicationEventPublisher publisher;

    public SpringDomainEvents(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(List<DomainEvent> events) {
        if (events.isEmpty()) return;
        // AFTER_COMMIT listeners silently drop events published outside a transaction.
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Domain events must be published inside a transaction");
        }
        events.forEach(publisher::publishEvent);
    }
}
