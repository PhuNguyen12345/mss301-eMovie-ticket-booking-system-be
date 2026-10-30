package fu.mss301.booking.application.port;

import java.time.LocalDateTime;

/** Payment provider boundary; the PayOS SDK never leaks past its adapter. */
public interface PaymentGateway {
    CheckoutLink createLink(LinkRequest request);

    LinkState getLinkState(long orderCode);

    void cancelLink(long orderCode, String reason);

    /** Verifies the provider signature; throws {@link fu.mss301.booking.application.exception.InvalidWebhookException}. */
    VerifiedPayment verifyWebhook(Object body);

    /** @param expiredAt epoch seconds after which the provider closes the link */
    record LinkRequest(long orderCode, long amount, String description, long expiredAt) {}

    record CheckoutLink(String paymentLinkId, String checkoutUrl) {}

    enum LinkStatus { PENDING, PAID, CANCELLED, EXPIRED, OTHER }

    record LinkState(LinkStatus status, long amountPaid, String reference, LocalDateTime paidAt) {}

    record VerifiedPayment(long orderCode, long amount, boolean success, String reference, LocalDateTime paidAt) {}
}
