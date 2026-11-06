package fu.mss301.user.application.projection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Routes an in-process event to every registered handler for its exact type. */
public final class EventIngestion {
    private final Map<Class<?>, List<ProjectionHandler<?>>> handlers;

    public EventIngestion(Collection<? extends ProjectionHandler<?>> registered) {
        Map<Class<?>, List<ProjectionHandler<?>>> grouped = new HashMap<>();
        for (ProjectionHandler<?> handler : registered) {
            Objects.requireNonNull(handler, "handler");
            grouped.computeIfAbsent(handler.eventType(), ignored -> new ArrayList<>()).add(handler);
        }
        Map<Class<?>, List<ProjectionHandler<?>>> immutable = new HashMap<>();
        grouped.forEach((type, matches) -> immutable.put(type, List.copyOf(matches)));
        handlers = Map.copyOf(immutable);
    }

    public void process(Object event) {
        for (ProjectionHandler<?> handler : handlersFor(event)) {
            processTyped(handler, event);
        }
    }

    public void reject(Object event) {
        for (ProjectionHandler<?> handler : handlersFor(event)) {
            rejectTyped(handler, event);
        }
    }

    private List<ProjectionHandler<?>> handlersFor(Object event) {
        Objects.requireNonNull(event, "event");
        List<ProjectionHandler<?>> matches = handlers.get(event.getClass());
        if (matches == null || matches.isEmpty()) {
            throw new IllegalArgumentException("No projection handler for " + event.getClass().getName());
        }
        return matches;
    }

    private static <E> void processTyped(ProjectionHandler<E> handler, Object event) {
        handler.process(handler.eventType().cast(event));
    }

    private static <E> void rejectTyped(ProjectionHandler<E> handler, Object event) {
        handler.onRejected(handler.eventType().cast(event));
    }
}
