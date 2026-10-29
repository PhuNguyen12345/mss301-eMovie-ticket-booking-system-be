package fu.mss301.booking.domain.payment;

import fu.mss301.booking.domain.shared.InvalidStateTransitionException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Child entity of {@link PaymentTransaction}; created and completed only through the root. */
@Entity
@Table(name = "refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    @Getter(AccessLevel.NONE)
    private PaymentTransaction payment;

    @Column(name = "amount", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RefundStatus status;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    static Refund request(PaymentTransaction payment, BigDecimal amount, String reason, LocalDateTime now) {
        Refund refund = new Refund();
        refund.payment = payment;
        refund.amount = amount;
        refund.reason = reason;
        refund.status = RefundStatus.PENDING;
        refund.createdAt = now;
        refund.updatedAt = now;
        return refund;
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
}
