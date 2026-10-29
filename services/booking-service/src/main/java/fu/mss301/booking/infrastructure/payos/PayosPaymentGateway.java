package fu.mss301.booking.infrastructure.payos;

import fu.mss301.booking.application.exception.InvalidWebhookException;
import fu.mss301.booking.application.exception.PaymentGatewayException;
import fu.mss301.booking.application.port.PaymentGateway;
import org.springframework.stereotype.Component;
import vn.payos.PayOS;
import vn.payos.exception.PayOSException;
import vn.payos.exception.WebhookException;
import vn.payos.exception.InvalidSignatureException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.Transaction;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;

/** PayOS adapter (payos-java v2). The SDK client is created lazily so the service starts without keys. */
@Component
class PayosPaymentGateway implements PaymentGateway {
    private static final DateTimeFormatter PAYOS_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SUCCESS_CODE = "00";

    private final PayosProperties properties;
    private volatile PayOS client;

    PayosPaymentGateway(PayosProperties properties) {
        this.properties = properties;
    }

    @Override
    public CheckoutLink createLink(LinkRequest request) {
        try {
            var response = client().paymentRequests().create(CreatePaymentLinkRequest.builder()
                .orderCode(request.orderCode())
                .amount(request.amount())
                .description(request.description())
                .returnUrl(properties.returnUrl())
                .cancelUrl(properties.cancelUrl())
                .expiredAt(request.expiredAt())
                .build());
            return new CheckoutLink(response.getPaymentLinkId(), response.getCheckoutUrl());
        } catch (PayOSException exception) {
            throw new PaymentGatewayException("Could not create PayOS payment link", exception);
        }
    }

    @Override
    public LinkState getLinkState(long orderCode) {
        try {
            PaymentLink link = client().paymentRequests().get(orderCode);
            Transaction latest = latest(link.getTransactions());
            return new LinkState(map(link), link.getAmountPaid() == null ? 0 : link.getAmountPaid(),
                latest == null ? null : latest.getReference(),
                latest == null ? null : parseTime(latest.getTransactionDateTime()));
        } catch (PayOSException exception) {
            throw new PaymentGatewayException("Could not read PayOS payment link " + orderCode, exception);
        }
    }

    @Override
    public void cancelLink(long orderCode, String reason) {
        try {
            client().paymentRequests().cancel(orderCode, reason);
        } catch (PayOSException exception) {
            throw new PaymentGatewayException("Could not cancel PayOS payment link " + orderCode, exception);
        }
    }

    @Override
    public VerifiedPayment verifyWebhook(Object body) {
        PayOS payos;
        try {
            payos = client();
        } catch (PaymentGatewayException exception) {
            throw new InvalidWebhookException("PayOS is not configured");
        }
        try {
            var data = payos.webhooks().verify(body);
            if (data == null || data.getOrderCode() == null || data.getAmount() == null) {
                throw new InvalidWebhookException("Webhook data is incomplete");
            }
            return new VerifiedPayment(data.getOrderCode(), data.getAmount(), SUCCESS_CODE.equals(data.getCode()),
                data.getReference(), parseTime(data.getTransactionDateTime()));
        } catch (WebhookException | InvalidSignatureException exception) {
            throw new InvalidWebhookException("Invalid PayOS webhook: " + exception.getMessage());
        }
    }

    private PayOS client() {
        PayOS current = client;
        if (current != null) return current;
        synchronized (this) {
            if (client == null) {
                if (!properties.configured()) {
                    throw new PaymentGatewayException("PayOS is not configured (PAYOS_CLIENT_ID/API_KEY/CHECKSUM_KEY)", null);
                }
                client = new PayOS(properties.clientId(), properties.apiKey(), properties.checksumKey());
            }
            return client;
        }
    }

    private static LinkStatus map(PaymentLink link) {
        if (link.getStatus() == null) return LinkStatus.OTHER;
        return switch (link.getStatus()) {
            case PAID -> LinkStatus.PAID;
            case PENDING, PROCESSING -> LinkStatus.PENDING;
            case CANCELLED -> LinkStatus.CANCELLED;
            case EXPIRED -> LinkStatus.EXPIRED;
            default -> LinkStatus.OTHER;
        };
    }

    private static Transaction latest(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) return null;
        return transactions.stream().max(Comparator.comparing(t -> String.valueOf(t.getTransactionDateTime())))
            .orElse(null);
    }

    /** PayOS sends "yyyy-MM-dd HH:mm:ss" in webhooks and ISO-8601 with offset in the link API. */
    static LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value, PAYOS_TIME);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value).toLocalDateTime();
            } catch (DateTimeParseException alsoIgnored) {
                return null;
            }
        }
    }
}
