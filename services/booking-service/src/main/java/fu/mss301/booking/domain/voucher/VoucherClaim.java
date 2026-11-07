package fu.mss301.booking.domain.voucher;

import java.time.LocalDateTime;

/** Child entity of {@link Voucher}: who owns the code. Each code has at most one owner. */
public final class VoucherClaim {
    private final Integer id;
    private final Integer userId;
    private final ClaimStatus status;
    private final LocalDateTime usedAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private VoucherClaim(Integer id, Integer userId, ClaimStatus status, LocalDateTime usedAt, LocalDateTime createdAt,
                         LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.status = status;
        this.usedAt = usedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static VoucherClaim of(Integer userId, LocalDateTime now) {
        return new VoucherClaim(null, userId, ClaimStatus.AVAILABLE, null, now, now);
    }

    /** Rebuilds a stored claim; performs no business validation. */
    public static VoucherClaim restore(Integer id, Integer userId, ClaimStatus status, LocalDateTime usedAt,
                                       LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new VoucherClaim(id, userId, status, usedAt, createdAt, updatedAt);
    }

    public Integer id() { return id; }
    public Integer userId() { return userId; }
    public ClaimStatus status() { return status; }
    public LocalDateTime usedAt() { return usedAt; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
}
