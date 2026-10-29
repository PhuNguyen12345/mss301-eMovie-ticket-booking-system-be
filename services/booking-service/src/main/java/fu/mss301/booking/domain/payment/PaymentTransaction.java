package fu.mss301.booking.domain.payment;

import fu.mss301.booking.domain.payment.event.PaymentSucceeded;
import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate root for one payment attempt of a booking through a payment provider (PayOS).
 * <p>
 * State machine: {@code PENDING -> SUCCESS | FAILED}, {@code FAILED -> SUCCESS} (money arrived after the
 * link was closed) and {@code SUCCESS -> REFUNDED}. It references its booking by id only: the booking is a
 * different aggregate and is updated through the {@link PaymentSucceeded} event.
 */
@Entity
@Table(name = "payment_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTransaction extends AbstractAggregateRoot<PaymentTransaction> {
    public static final String PROVIDER_PAYOS = "PAYOS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private Integer bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, updatable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "provider", nullable = false, updatable = false, length = 50)
    private String provider;

    @Column(name = "order_code", nullable = false, updatable = false)
    private Long orderCode;

    @Column(name = "payment_link_id")
    private String paymentLinkId;

    @Column(name = "provider_transaction_id")
    private String providerTransactionId;

    @Column(name = "checkout_url", length = 500)
    private String checkoutUrl;

    @Column(name = "amount", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Version
    @Getter(AccessLevel.NONE)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Getter(AccessLevel.NONE)
    private List<Refund> refunds = new ArrayList<>();

    public static PaymentTransaction initiate(Integer bookingId, BigDecimal amount, Long orderCode, LocalDateTime now) {
        if (bookingId == null || bookingId <= 0) throw new DomainException("Booking id must be positive");
        if (amount == null || amount.signum() <= 0) throw new DomainException("Payment amount must be positive");
        if (orderCode == null || orderCode <= 0) throw new DomainException("Order code must be positive");
        PaymentTransaction payment = new PaymentTransaction();
        payment.bookingId = bookingId;
        payment.paymentMethod = PaymentMethod.BANK_TRANSFER;
        payment.provider = PROVIDER_PAYOS;
        payment.orderCode = orderCode;
        payment.amount = amount;
        payment.status = PaymentStatus.PENDING;
        payment.createdAt = now;
        payment.updatedAt = now;
        return payment;
    }

    public void attachCheckout(String paymentLinkId, String checkoutUrl, LocalDateTime now) {
        if (status != PaymentStatus.PENDING) {
            throw new InvalidStateTransitionException("Checkout link can only be attached to a pending payment");
        }
        if (checkoutUrl == null || checkoutUrl.isBlank()) throw new DomainException("Checkout URL is required");
        this.paymentLinkId = paymentLinkId;
        this.checkoutUrl = checkoutUrl;
        this.updatedAt = now;
    }

    /**
     * Records the provider-confirmed payment. Idempotent: a repeated notification for an already settled
     * payment is ignored and returns {@code false}.
     */
    public boolean markSucceeded(BigDecimal paidAmount, String reference, LocalDateTime paidAt, LocalDateTime now) {
        if (status == PaymentStatus.SUCCESS || status == PaymentStatus.REFUNDED) return false;
        if (paidAmount == null || paidAmount.compareTo(amount) != 0) {
            throw new DomainException("Paid amount " + paidAmount + " does not match payment amount " + amount);
        }
        this.status = PaymentStatus.SUCCESS;
        this.providerTransactionId = reference;
        this.paidAt = paidAt;
        this.updatedAt = now;
        registerEvent(new PaymentSucceeded(id, bookingId));
        return true;
    }

    /** Closes a pending attempt (booking expired/cancelled or link creation failed). No-op otherwise. */
    public boolean markFailed(LocalDateTime now) {
        if (status != PaymentStatus.PENDING) return false;
        this.status = PaymentStatus.FAILED;
        this.updatedAt = now;
        return true;
    }

    /** Money was received for a booking that can no longer be confirmed. Idempotent per payment. */
    public boolean requestRefund(String reason, LocalDateTime now) {
        if (status != PaymentStatus.SUCCESS) {
            throw new InvalidStateTransitionException("Only a successful payment can be refunded");
        }
        if (refunds.stream().anyMatch(Refund::isOpenOrDone)) return false;
        refunds.add(Refund.request(this, amount, reason, now));
        this.updatedAt = now;
        return true;
    }

    /** Refunds are paid out manually (PayOS has no refund API); an admin confirms it here. */
    public void completeRefund(Integer refundId, LocalDateTime now) {
        Refund refund = refunds.stream().filter(r -> r.getId().equals(refundId)).findFirst()
            .orElseThrow(() -> new DomainException("Refund " + refundId + " does not belong to this payment"));
        refund.complete(now);
        this.status = PaymentStatus.REFUNDED;
        this.updatedAt = now;
    }

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    public List<Refund> getRefunds() {
        return Collections.unmodifiableList(refunds);
    }
}
