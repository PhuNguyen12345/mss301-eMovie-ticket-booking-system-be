package fu.mss301.booking.domain.booking;

import fu.mss301.booking.domain.voucher.DiscountType;
import fu.mss301.booking.domain.voucher.VoucherKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Child entity of {@link Booking}: a voucher applied to it, with a copy of the voucher's terms and the amount it
 * takes off right now. Only the aggregate root changes it.
 */
public final class BookingVoucher {
    private final Integer id;
    private final VoucherOffer offer;
    /** The seat a ticket exchange voucher pays for, otherwise null. */
    private final Integer seatId;
    private BigDecimal discountAmount;
    private final LocalDateTime createdAt;

    private BookingVoucher(Integer id, VoucherOffer offer, Integer seatId, BigDecimal discountAmount,
                           LocalDateTime createdAt) {
        this.id = id;
        this.offer = offer;
        this.seatId = seatId;
        this.discountAmount = discountAmount;
        this.createdAt = createdAt;
    }

    static BookingVoucher apply(VoucherOffer offer, Integer seatId, LocalDateTime now) {
        return new BookingVoucher(null, offer, seatId, BigDecimal.ZERO.setScale(2), now);
    }

    /** Rebuilds a stored line; performs no business validation. */
    public static BookingVoucher restore(Integer id, VoucherOffer offer, Integer seatId, BigDecimal discountAmount,
                                         LocalDateTime createdAt) {
        return new BookingVoucher(id, offer, seatId, discountAmount, createdAt);
    }

    void priceAt(BigDecimal amount) {
        discountAmount = amount.setScale(2);
    }

    boolean isExpiredOn(LocalDate today) {
        return today.isAfter(offer.expiresOn());
    }

    public Integer id() { return id; }
    public Integer voucherId() { return offer.voucherId(); }
    public String code() { return offer.code(); }
    public VoucherKind kind() { return offer.kind(); }
    public DiscountType discountType() { return offer.discountType(); }
    public BigDecimal discountValue() { return offer.discountValue(); }
    public BigDecimal minOrderAmount() { return offer.minOrderAmount(); }
    public Integer seatTypeId() { return offer.seatTypeId(); }
    public Integer comboId() { return offer.comboId(); }
    public LocalDate expiresOn() { return offer.expiresOn(); }
    public Integer seatId() { return seatId; }
    public BigDecimal discountAmount() { return discountAmount; }
    public LocalDateTime createdAt() { return createdAt; }
}
