package fu.mss301.user.infrastructure.notification;

import java.security.SecureRandom;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class OtpGenerator implements fu.mss301.user.application.port.out.OtpCodeGenerator {
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    }
}
