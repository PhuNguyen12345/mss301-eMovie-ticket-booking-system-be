package fu.mss301.booking.domain.voucher;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Child entity of {@link Voucher}: who owns the code and whether a booking holds it. Each code has at most one
 * owner. Only the aggregate root changes it.
 */
public final class VoucherClaim {
    private final Integer id;
    private final Integer userId;
    private ClaimStatus status;
    /** The booking holding or having used the voucher, null while it is available. */
    private Integer bookingId;
    private LocalDateTime usedAt;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private VoucherClaim(Integer id, Integer userId, ClaimStatus status, Integer bookingId, LocalDateTime usedAt,
                         LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.status = status;
        this.bookingId = bookingId;
        this.usedAt = usedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static VoucherClaim of(Integer userId, LocalDateTime now) {
        return new VoucherClaim(null, userId, ClaimStatus.AVAILABLE, null, null, now, now);
    }

    /** Rebuilds a stored claim; performs no business validation. */
    public static VoucherClaim restore(Integer id, Integer userId, ClaimStatus status, Integer bookingId,
                                       LocalDateTime usedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new VoucherClaim(id, userId, status, bookingId, usedAt, createdAt, updatedAt);
    }

    boolean isHeldBy(Integer booking) {
        return status != ClaimStatus.AVAILABLE && Objects.equals(bookingId, booking);
    }

    void reserve(Integer booking, LocalDateTime now) {
        status = ClaimStatus.RESERVED;
        bookingId = booking;
        updatedAt = now;
    }

    void release(LocalDateTime now) {
        status = ClaimStatus.AVAILABLE;
        bookingId = null;
        updatedAt = now;
    }

    void use(Integer booking, LocalDateTime now) {
        status = ClaimStatus.USED;
        bookingId = booking;
        usedAt = now;
        updatedAt = now;
    }

    public Integer id() { return id; }
    public Integer userId() { return userId; }
    public ClaimStatus status() { return status; }
    public Integer bookingId() { return bookingId; }
    public LocalDateTime usedAt() { return usedAt; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
}
