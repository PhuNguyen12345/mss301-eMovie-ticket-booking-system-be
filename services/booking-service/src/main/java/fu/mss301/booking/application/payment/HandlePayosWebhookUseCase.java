package fu.mss301.booking.application.payment;

import fu.mss301.booking.application.port.PaymentGateway;

import java.lang.System.Logger.Level;

/** Entry point for PayOS payment notifications. Only signature-verified data is trusted. */
public class HandlePayosWebhookUseCase {
    private static final System.Logger log = System.getLogger(HandlePayosWebhookUseCase.class.getName());
    private final PaymentGateway gateway;
    private final PaymentSettlementService settlement;

    public HandlePayosWebhookUseCase(PaymentGateway gateway, PaymentSettlementService settlement) {
        this.gateway = gateway;
        this.settlement = settlement;
    }

    public void handle(Object body) {
        var payment = gateway.verifyWebhook(body);
        if (!payment.success()) {
            log.log(Level.INFO, () -> "PayOS reported a non-successful payment for order " + payment.orderCode());
            return;
        }
        if (!settlement.recordSuccess(payment.orderCode(), payment.amount(), payment.reference(), payment.paidAt())) {
            log.log(Level.WARNING, () -> "PayOS webhook for unknown order code " + payment.orderCode() + " ignored");
        }
    }
}
