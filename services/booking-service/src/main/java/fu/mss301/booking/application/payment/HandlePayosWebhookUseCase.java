package fu.mss301.booking.application.payment;

import fu.mss301.booking.application.port.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Entry point for PayOS payment notifications. Only signature-verified data is trusted. */
@Service
public class HandlePayosWebhookUseCase {
    private static final Logger log = LoggerFactory.getLogger(HandlePayosWebhookUseCase.class);
    private final PaymentGateway gateway;
    private final PaymentSettlementService settlement;

    public HandlePayosWebhookUseCase(PaymentGateway gateway, PaymentSettlementService settlement) {
        this.gateway = gateway;
        this.settlement = settlement;
    }

    public void handle(Object body) {
        var payment = gateway.verifyWebhook(body);
        if (!payment.success()) {
            log.info("PayOS reported a non-successful payment for order {}", payment.orderCode());
            return;
        }
        if (!settlement.recordSuccess(payment.orderCode(), payment.amount(), payment.reference(), payment.paidAt())) {
            log.warn("PayOS webhook for unknown order code {} ignored", payment.orderCode());
        }
    }
}
