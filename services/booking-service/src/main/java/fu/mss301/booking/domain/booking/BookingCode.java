package fu.mss301.booking.domain.booking;

import fu.mss301.booking.domain.shared.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Human-readable booking reference shown on tickets, e.g. {@code BK260129103000X7K2QD}. */
@Embeddable
public record BookingCode(@Column(name = "booking_code", nullable = false, updatable = false, length = 50) String value) {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    public BookingCode {
        if (value == null || !value.matches("BK[0-9A-Z]{6,48}")) {
            throw new DomainException("Invalid booking code");
        }
    }

    public static BookingCode generate(LocalDateTime now) {
        StringBuilder suffix = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            suffix.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return new BookingCode("BK" + now.format(STAMP) + suffix);
    }

    @Override
    public String toString() {
        return value;
    }
}
