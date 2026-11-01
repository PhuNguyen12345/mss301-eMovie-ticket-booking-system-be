package fu.mss301.user.application.projection;

public interface ProjectionHandler<E> {
    Class<E> eventType();
    void process(E event);
    void onRejected(E event);
}
