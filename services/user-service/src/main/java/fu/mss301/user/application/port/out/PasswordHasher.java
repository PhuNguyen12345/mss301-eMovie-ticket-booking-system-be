package fu.mss301.user.application.port.out;

public interface PasswordHasher {
    String encode(CharSequence raw);
    boolean matches(CharSequence raw, String encoded);
}
