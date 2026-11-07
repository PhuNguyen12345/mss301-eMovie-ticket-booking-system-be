package fu.mss301.user.application.port.out;

/** External delivery and status persistence for one OTP event type. */
public interface OtpDeliveryPort<E> {
    Class<E> eventType();
    void send(E event);
    void markFailed(E event);
}
