package fu.mss301.booking.domain.combo;

import fu.mss301.booking.domain.shared.DomainException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Aggregate root for one food and drink combo on the menu. Bookings keep their own copy of its price. */
public final class Combo {
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999");

    private final Integer id;
    private String name;
    private String description;
    private BigDecimal price;
    private ComboStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Combo(Integer id, String name, String description, BigDecimal price, ComboStatus status,
                  LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Combo create(String name, String description, BigDecimal price, LocalDateTime now) {
        validate(name, description, price);
        return new Combo(null, name.strip(), description, price.setScale(2), ComboStatus.ACTIVE, now, now);
    }

    /** Rebuilds a stored combo; performs no business validation. */
    public static Combo restore(Integer id, String name, String description, BigDecimal price, ComboStatus status,
                                LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new Combo(id, name, description, price, status, createdAt, updatedAt);
    }

    public void update(String name, String description, BigDecimal price, LocalDateTime now) {
        validate(name, description, price);
        this.name = name.strip();
        this.description = description;
        this.price = price.setScale(2);
        this.updatedAt = now;
    }

    public void activate(LocalDateTime now) {
        status = ComboStatus.ACTIVE;
        updatedAt = now;
    }

    public void deactivate(LocalDateTime now) {
        status = ComboStatus.INACTIVE;
        updatedAt = now;
    }

    public boolean isOnSale() {
        return status == ComboStatus.ACTIVE;
    }

    private static void validate(String name, String description, BigDecimal price) {
        if (name == null || name.isBlank() || name.strip().length() > 100) {
            throw new DomainException("Combo name must have 1 to 100 characters");
        }
        if (description != null && description.length() > 255) {
            throw new DomainException("Combo description must have at most 255 characters");
        }
        if (price == null || price.signum() <= 0 || price.compareTo(MAX_PRICE) > 0) {
            throw new DomainException("Combo price must be between 1 and " + MAX_PRICE.toPlainString());
        }
        // PayOS only accepts whole VND, so a fractional price would make the booking impossible to pay.
        if (price.stripTrailingZeros().scale() > 0) throw new DomainException("Combo price must be a whole VND amount");
    }

    public Integer id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public BigDecimal price() { return price; }
    public ComboStatus status() { return status; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
}
