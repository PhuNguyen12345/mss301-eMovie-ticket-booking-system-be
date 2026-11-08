package fu.mss301.booking.domain.voucher;

import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Aggregate root for one voucher code. A code belongs to one account and is used once. */
public final class Voucher {
    private final Integer id;
    private final VoucherCode code;
    private VoucherTerms terms;
    private VoucherStatus status;
    private final Integer version;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private VoucherClaim claim;

    private Voucher(Integer id, VoucherCode code, VoucherTerms terms, VoucherStatus status, Integer version,
                    LocalDateTime createdAt, LocalDateTime updatedAt, VoucherClaim claim) {
        this.id = id;
        this.code = code;
        this.terms = terms;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.claim = claim;
    }

    public static Voucher create(VoucherCode code, VoucherTerms terms, LocalDateTime now) {
        if (code == null) throw new DomainException("Voucher code is required");
        if (terms == null) throw new DomainException("Voucher terms are required");
        requireAdminKind(terms);
        return new Voucher(null, code, terms, VoucherStatus.ACTIVE, null, now, now, null);
    }

    /** Rebuilds a stored voucher; performs no business validation. */
    public static Voucher restore(Integer id, VoucherCode code, VoucherTerms terms, VoucherStatus status,
                                  Integer version, LocalDateTime createdAt, LocalDateTime updatedAt, VoucherClaim claim) {
        return new Voucher(id, code, terms, status, version, createdAt, updatedAt, claim);
    }

    /** Terms are frozen once someone owns the code, so a customer's voucher never changes under them. */
    public void update(VoucherTerms replacement, LocalDateTime now) {
        if (replacement == null) throw new DomainException("Voucher terms are required");
        requireAdminKind(replacement);
        if (claim != null) throw new InvalidStateTransitionException("A voucher with an owner cannot be changed");
        terms = replacement;
        updatedAt = now;
    }

    public void activate(LocalDateTime now) {
        status = VoucherStatus.ACTIVE;
        updatedAt = now;
    }

    public void deactivate(LocalDateTime now) {
        status = VoucherStatus.INACTIVE;
        updatedAt = now;
    }

    public void assignTo(Integer userId, LocalDateTime now) {
        if (userId == null || userId <= 0) throw new DomainException("User id must be positive");
        if (claim != null) throw new InvalidStateTransitionException("This voucher already has an owner");
        if (!isClaimableOn(now.toLocalDate())) throw new InvalidStateTransitionException("This voucher cannot be assigned");
        claim = VoucherClaim.of(userId, now);
        updatedAt = now;
    }

    /**
     * Holds the voucher for one pending booking of its owner, until that booking is paid or gives it back.
     * Holding it again for the same booking changes nothing.
     */
    public void reserveFor(Integer userId, Integer bookingId, LocalDateTime now) {
        if (bookingId == null) throw new DomainException("Booking id is required");
        if (!isOwnedBy(userId)) throw new InvalidStateTransitionException("This voucher belongs to another account");
        if (claim.status() == ClaimStatus.RESERVED && bookingId.equals(claim.bookingId())) return;
        if (claim.status() != ClaimStatus.AVAILABLE) throw new InvalidStateTransitionException("This voucher is already in use");
        if (status != VoucherStatus.ACTIVE) throw new InvalidStateTransitionException("This voucher is no longer active");
        LocalDate today = now.toLocalDate();
        if (today.isBefore(terms.startDate())) {
            throw new DomainException("This voucher can be used from " + terms.startDate());
        }
        if (isExpiredOn(today)) throw new DomainException("This voucher has expired");
        claim.reserve(bookingId, now);
        updatedAt = now;
    }

    /**
     * Gives the voucher back to its owner when the booking holding it did not use it. An expired voucher is
     * given back too but can no longer be applied.
     *
     * @return true when the voucher changed
     */
    public boolean releaseFrom(Integer bookingId, LocalDateTime now) {
        if (claim == null || claim.status() != ClaimStatus.RESERVED || !claim.isHeldBy(bookingId)) return false;
        claim.release(now);
        updatedAt = now;
        return true;
    }

    /**
     * The booking holding the voucher was paid. A paid booking is the record of what the customer got, so a
     * voucher that was given back by mistake is marked used as well.
     *
     * @return true when the voucher changed; false when it is already used or another booking holds it
     */
    public boolean markUsedBy(Integer bookingId, LocalDateTime now) {
        if (claim == null || claim.status() == ClaimStatus.USED) return false;
        if (claim.status() == ClaimStatus.RESERVED && !claim.isHeldBy(bookingId)) return false;
        claim.use(bookingId, now);
        updatedAt = now;
        return true;
    }

    public boolean isOwnedBy(Integer userId) {
        return claim != null && claim.userId().equals(userId);
    }

    public boolean isClaimableOn(LocalDate today) {
        return claim == null && status == VoucherStatus.ACTIVE && !isExpiredOn(today);
    }

    /** The end date is the last usable day. */
    public boolean isExpiredOn(LocalDate today) {
        return today.isAfter(terms.endDate());
    }

    public VoucherAvailability availabilityOn(LocalDate today) {
        if (claim != null && claim.status() == ClaimStatus.USED) return VoucherAvailability.USED;
        if (claim != null && claim.status() == ClaimStatus.RESERVED) return VoucherAvailability.RESERVED;
        if (isExpiredOn(today)) return VoucherAvailability.EXPIRED;
        if (status == VoucherStatus.INACTIVE) return VoucherAvailability.INACTIVE;
        return VoucherAvailability.AVAILABLE;
    }

    private static void requireAdminKind(VoucherTerms terms) {
        if (terms.kind() == VoucherKind.VALUE) throw new DomainException("Value vouchers are issued by the system");
    }

    public Integer id() { return id; }
    public VoucherCode code() { return code; }
    public VoucherTerms terms() { return terms; }
    public VoucherStatus status() { return status; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
    public VoucherClaim claim() { return claim; }

    /** Optimistic-lock token owned by persistence; the domain never interprets it. */
    public Integer version() { return version; }
}
