package fu.mss301.user.application.projection;

import fu.mss301.user.application.port.out.OtpDeliveryPort;

/** Projects an OTP request into its persisted delivery status through an output port. */
public final class OtpDeliveryProjectionHandler<E> implements ProjectionHandler<E> {
    private final OtpDeliveryPort<E> delivery;

    public OtpDeliveryProjectionHandler(OtpDeliveryPort<E> delivery) {
        this.delivery = delivery;
    }

    @Override
    public Class<E> eventType() {
        return delivery.eventType();
    }

    @Override
    public void process(E event) {
        delivery.send(event);
    }

    @Override
    public void onRejected(E event) {
        delivery.markFailed(event);
    }
}
