package fu.mss301.user.domain.valueobject;

import fu.mss301.user.domain.exception.DomainException;
import java.util.Locale;

/** Canonical email address used by user account workflows. */
public record Email(String value) {
    public Email {
        if (value == null || value.isBlank()) {
            throw new DomainException("Email is required");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        int at = value.indexOf('@');
        if (value.length() > 255 || at < 1 || at == value.length() - 1
                || value.indexOf('@', at + 1) >= 0 || value.chars().anyMatch(Character::isWhitespace)) {
            throw new DomainException("Invalid email address");
        }
    }

    public static Email of(String value) {
        return new Email(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
