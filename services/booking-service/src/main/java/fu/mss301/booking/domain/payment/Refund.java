package fu.mss301.booking.domain.payment;

import fu.mss301.booking.domain.shared.InvalidStateTransitionException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Child entity of {@link PaymentTransaction}; created and completed only through the root. */
public final class Refund {
    private final Integer id;
    private final BigDecimal amount;
    private final String reason;
    private final RefundMethod method;
    /** The value voucher given instead of money, or null. */
    private final Integer voucherId;
    private RefundStatus status;
    private LocalDateTime refundedAt;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Refund(Integer id, BigDecimal amount, String reason, RefundMethod method, Integer voucherId,
                   RefundStatus status, LocalDateTime refundedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.amount = amount;
        this.reason = reason;
        this.method = method;
        this.voucherId = voucherId;
        this.status = status;
        this.refundedAt = refundedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static Refund request(BigDecimal amount, String reason, LocalDateTime now) {
        return new Refund(null, amount, reason, RefundMethod.MANUAL, null, RefundStatus.PENDING, null, now, now);
    }

    /** Given back as a value voucher, so it is done at once. */
    static Refund byVoucher(BigDecimal amount, String reason, Integer voucherId, LocalDateTime now) {
        return new Refund(null, amount, reason, RefundMethod.VOUCHER, voucherId, RefundStatus.COMPLETED, now, now, now);
    }

    /** Rebuilds a stored refund; performs no business validation. */
    public static Refund restore(Integer id, BigDecimal amount, String reason, RefundMethod method, Integer voucherId,
                                 RefundStatus status, LocalDateTime refundedAt, LocalDateTime createdAt,
                                 LocalDateTime updatedAt) {
        return new Refund(id, amount, reason, method, voucherId, status, refundedAt, createdAt, updatedAt);
    }

    void complete(LocalDateTime now) {
        if (status != RefundStatus.PENDING) {
            throw new InvalidStateTransitionException("Only a pending refund can be completed");
        }
        status = RefundStatus.COMPLETED;
        refundedAt = now;
        updatedAt = now;
    }

    boolean isOpenOrDone() {
        return status != RefundStatus.REJECTED;
    }

    public Integer id() { return id; }
    public BigDecimal amount() { return amount; }
    public String reason() { return reason; }
    public RefundMethod method() { return method; }
    public Integer voucherId() { return voucherId; }
    public RefundStatus status() { return status; }
    public LocalDateTime refundedAt() { return refundedAt; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
}
