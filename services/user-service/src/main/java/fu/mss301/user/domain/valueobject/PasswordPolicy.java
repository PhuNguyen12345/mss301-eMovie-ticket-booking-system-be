package fu.mss301.user.domain.valueobject;

import java.nio.charset.StandardCharsets;
public final class PasswordPolicy {
    private PasswordPolicy() {}
    public static boolean tooLong(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > 72;
    }
}
