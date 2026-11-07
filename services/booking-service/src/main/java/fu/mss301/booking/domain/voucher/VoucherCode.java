package fu.mss301.booking.domain.voucher;

import fu.mss301.booking.domain.shared.DomainException;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

/** The code a customer types. Stored in upper case, so typing it in lower case still matches. */
public record VoucherCode(String value) {
    private static final Pattern FORMAT = Pattern.compile("[A-Z0-9-]{4,50}");
    /** Leaves out 0, O, 1 and I, which are easy to mistype. */
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int GENERATED_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    public VoucherCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new DomainException("Voucher code must have 4 to 50 letters, digits or dashes");
        }
    }

    public static VoucherCode of(String input) {
        return new VoucherCode(normalize(input));
    }

    public static VoucherCode generate() {
        var code = new StringBuilder("EM");
        for (int i = 0; i < GENERATED_LENGTH; i++) code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return new VoucherCode(code.toString());
    }

    /** For lookups: a mistyped code simply finds nothing. */
    public static String normalize(String input) {
        return input == null ? null : input.strip().toUpperCase(Locale.ROOT);
    }
}
