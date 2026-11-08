package fu.mss301.booking.domain.payment;

import fu.mss301.booking.domain.payment.event.PaymentSucceeded;
import fu.mss301.booking.domain.shared.DomainEvent;
import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate root for one payment attempt of a booking through a payment provider (PayOS).
 * <p>
 * State machine: {@code PENDING -> SUCCESS | FAILED}, {@code FAILED -> SUCCESS} (money arrived after the
 * link was closed) and {@code SUCCESS -> REFUNDED} (given back as a voucher, or by an older manual refund).
 * It references its booking by id only: the booking is a
 * different aggregate and is updated through the {@link PaymentSucceeded} event.
 */
public final class PaymentTransaction {
    public static final String PROVIDER_PAYOS = "PAYOS";

    private final Integer id;
    private final Integer bookingId;
    private final PaymentMethod paymentMethod;
    private final String provider;
    private final Long orderCode;
    private String paymentLinkId;
    private String providerTransactionId;
    private String checkoutUrl;
    private final BigDecimal amount;
    private PaymentStatus status;
    private LocalDateTime paidAt;
    private final Integer version;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private final List<Refund> refunds;
    private final List<DomainEvent> events = new ArrayList<>();

    private PaymentTransaction(Integer id, Integer bookingId, PaymentMethod paymentMethod, String provider,
                               Long orderCode, String paymentLinkId, String providerTransactionId, String checkoutUrl,
                               BigDecimal amount, PaymentStatus status, LocalDateTime paidAt, Integer version,
                               LocalDateTime createdAt, LocalDateTime updatedAt, List<Refund> refunds) {
        this.id = id;
        this.bookingId = bookingId;
        this.paymentMethod = paymentMethod;
        this.provider = provider;
        this.orderCode = orderCode;
        this.paymentLinkId = paymentLinkId;
        this.providerTransactionId = providerTransactionId;
        this.checkoutUrl = checkoutUrl;
        this.amount = amount;
        this.status = status;
        this.paidAt = paidAt;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.refunds = new ArrayList<>(refunds);
    }

    public static PaymentTransaction initiate(Integer bookingId, BigDecimal amount, Long orderCode, LocalDateTime now) {
        if (bookingId == null || bookingId <= 0) throw new DomainException("Booking id must be positive");
        if (amount == null || amount.signum() <= 0) throw new DomainException("Payment amount must be positive");
        if (orderCode == null || orderCode <= 0) throw new DomainException("Order code must be positive");
        return new PaymentTransaction(null, bookingId, PaymentMethod.BANK_TRANSFER, PROVIDER_PAYOS, orderCode, null,
            null, null, amount, PaymentStatus.PENDING, null, null, now, now, List.of());
    }

    /** Rebuilds a stored payment; performs no business validation. */
    public static PaymentTransaction restore(Integer id, Integer bookingId, PaymentMethod paymentMethod,
                                             String provider, Long orderCode, String paymentLinkId,
                                             String providerTransactionId, String checkoutUrl, BigDecimal amount,
                                             PaymentStatus status, LocalDateTime paidAt, Integer version,
                                             LocalDateTime createdAt, LocalDateTime updatedAt, List<Refund> refunds) {
        return new PaymentTransaction(id, bookingId, paymentMethod, provider, orderCode, paymentLinkId,
            providerTransactionId, checkoutUrl, amount, status, paidAt, version, createdAt, updatedAt, refunds);
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
        events.add(new PaymentSucceeded(id, bookingId));
        return true;
    }

    /** Closes a pending attempt (booking expired/cancelled or link creation failed). No-op otherwise. */
    public boolean markFailed(LocalDateTime now) {
        if (status != PaymentStatus.PENDING) return false;
        this.status = PaymentStatus.FAILED;
        this.updatedAt = now;
        return true;
    }

    /**
     * Money was received for a booking that can no longer be confirmed, and is given back as a value voucher.
     * Idempotent per payment; a payment that already has a refund keeps it.
     *
     * @return true when the payment changed
     */
    public boolean compensateWithVoucher(Integer voucherId, String reason, LocalDateTime now) {
        if (voucherId == null) throw new DomainException("Voucher id is required");
        if (refunds.stream().anyMatch(Refund::isOpenOrDone)) return false;
        if (status != PaymentStatus.SUCCESS) {
            throw new InvalidStateTransitionException("Only a successful payment can be given back");
        }
        refunds.add(Refund.byVoucher(amount, reason, voucherId, now));
        this.status = PaymentStatus.REFUNDED;
        this.updatedAt = now;
        return true;
    }

    /**
     * Staff transfer the money back manually, then an admin confirms it here. Only refunds recorded before
     * late payments became vouchers are still pending.
     */
    public void completeRefund(Integer refundId, LocalDateTime now) {
        Refund refund = refunds.stream().filter(r -> refundId.equals(r.id())).findFirst()
            .orElseThrow(() -> new DomainException("Refund " + refundId + " does not belong to this payment"));
        refund.complete(now);
        this.status = PaymentStatus.REFUNDED;
        this.updatedAt = now;
    }

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    /** Returns the events raised since the last call and forgets them, so each is published once. */
    public List<DomainEvent> pullEvents() {
        var pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }

    public Integer id() { return id; }
    public Integer bookingId() { return bookingId; }
    public PaymentMethod paymentMethod() { return paymentMethod; }
    public String provider() { return provider; }
    public Long orderCode() { return orderCode; }
    public String paymentLinkId() { return paymentLinkId; }
    public String providerTransactionId() { return providerTransactionId; }
    public String checkoutUrl() { return checkoutUrl; }
    public BigDecimal amount() { return amount; }
    public PaymentStatus status() { return status; }
    public LocalDateTime paidAt() { return paidAt; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
    public List<Refund> refunds() { return Collections.unmodifiableList(refunds); }

    /** Optimistic-lock token owned by persistence; the domain never interprets it. */
    public Integer version() { return version; }
}
