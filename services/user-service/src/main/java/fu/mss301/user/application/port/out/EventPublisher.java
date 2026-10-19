package fu.mss301.user.application.port.out;

public interface EventPublisher {
    void publishEvent(Object event);
}
