package fu.mss301.showtime.domain.model;

import java.math.BigDecimal;

public record Price(BigDecimal amount) {
    public Price {
        if (amount == null || amount.signum() < 0 || amount.compareTo(new BigDecimal("9999999999.99")) > 0
                || amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Price must be non-negative, at most 9999999999.99, with up to 2 decimal places");
        }
        amount = amount.setScale(2);
    }
}
