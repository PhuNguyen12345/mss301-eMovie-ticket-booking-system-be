package fu.mss301.booking.infrastructure.payos;

import fu.mss301.booking.application.exception.InvalidWebhookException;
import fu.mss301.booking.application.port.PaymentGateway.VerifiedPayment;
import org.junit.jupiter.api.Test;
import vn.payos.crypto.CryptoProviderImpl;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PayosPaymentGatewayTest {
    private static final String CHECKSUM_KEY = "test-checksum-key-0123456789abcdef";
    private final PayosPaymentGateway gateway = new PayosPaymentGateway(
        new PayosProperties("client", "api-key", CHECKSUM_KEY, "http://localhost/return", "http://localhost/cancel"));

    @Test
    void acceptsCorrectlySignedWebhook() {
        VerifiedPayment payment = gateway.verifyWebhook(webhook(data(), CHECKSUM_KEY));
        assertEquals(1201L, payment.orderCode());
        assertEquals(180000L, payment.amount());
        assertTrue(payment.success());
        assertEquals("FT25001", payment.reference());
        assertEquals(LocalDateTime.of(2027, 1, 20, 18, 3, 15), payment.paidAt());
    }

    @Test
    void rejectsTamperedOrForeignSignature() {
        Map<String, Object> tampered = webhook(data(), CHECKSUM_KEY);
        @SuppressWarnings("unchecked") var data = (Map<String, Object>) tampered.get("data");
        data.put("amount", 1000);
        assertThrows(InvalidWebhookException.class, () -> gateway.verifyWebhook(tampered));
        assertThrows(InvalidWebhookException.class, () -> gateway.verifyWebhook(webhook(data(), "another-key")));
    }

    @Test
    void notConfiguredGatewayRejectsWebhooks() {
        var unconfigured = new PayosPaymentGateway(new PayosProperties("", "", "", "", ""));
        assertThrows(InvalidWebhookException.class, () -> unconfigured.verifyWebhook(webhook(data(), CHECKSUM_KEY)));
    }

    private static Map<String, Object> data() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderCode", 1201);
        data.put("amount", 180000);
        data.put("description", "BK12");
        data.put("accountNumber", "12345678");
        data.put("reference", "FT25001");
        data.put("transactionDateTime", "2027-01-20 18:03:15");
        data.put("currency", "VND");
        data.put("paymentLinkId", "link-12");
        data.put("code", "00");
        data.put("desc", "success");
        data.put("counterAccountBankId", "");
        data.put("counterAccountBankName", "");
        data.put("counterAccountName", "");
        data.put("counterAccountNumber", "");
        data.put("virtualAccountName", "");
        data.put("virtualAccountNumber", "");
        return data;
    }

    static Map<String, Object> webhook(Map<String, Object> data, String key) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "00");
        body.put("desc", "success");
        body.put("success", true);
        body.put("data", data);
        body.put("signature", new CryptoProviderImpl().createSignatureFromObj(data, key));
        return body;
    }
}
