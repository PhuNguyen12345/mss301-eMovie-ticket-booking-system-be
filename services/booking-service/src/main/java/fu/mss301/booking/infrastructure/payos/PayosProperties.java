package fu.mss301.booking.infrastructure.payos;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payos")
public record PayosProperties(String clientId, String apiKey, String checksumKey, String returnUrl, String cancelUrl) {
    boolean configured() {
        return notBlank(clientId) && notBlank(apiKey) && notBlank(checksumKey);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
