package fu.mss301.user.infrastructure.security;

import fu.mss301.user.application.port.out.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SpringPasswordHasher implements PasswordHasher {
    private final PasswordEncoder encoder;

    public SpringPasswordHasher(PasswordEncoder encoder) { this.encoder = encoder; }
    public String encode(CharSequence raw) { return encoder.encode(raw); }
    public boolean matches(CharSequence raw, String encoded) { return encoder.matches(raw, encoded); }
}
