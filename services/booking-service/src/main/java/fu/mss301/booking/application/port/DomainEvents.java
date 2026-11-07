package fu.mss301.booking.application.port;

import fu.mss301.booking.domain.shared.DomainEvent;

import java.util.List;

/**
 * Hands aggregate events to the reactions that run after commit. Call it inside the transaction that saved
 * the aggregate, with the events pulled from the object passed to {@code save}: the object {@code save}
 * returns is a fresh copy without events.
 */
public interface DomainEvents {
    void publish(List<DomainEvent> events);
}
