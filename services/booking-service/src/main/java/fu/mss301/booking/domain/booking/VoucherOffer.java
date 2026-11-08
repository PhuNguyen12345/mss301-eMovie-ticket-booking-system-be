package fu.mss301.booking.domain.booking;

import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.voucher.DiscountType;
import fu.mss301.booking.domain.voucher.VoucherKind;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A copy of a voucher's terms, handed to a booking after the voucher was reserved for it. The booking keeps
 * this copy, so pricing never reads the Voucher aggregate.
 *
 * @param expiresOn the last day the voucher can be used
 */
public record VoucherOffer(Integer voucherId, String code, VoucherKind kind, DiscountType discountType,
                           BigDecimal discountValue, BigDecimal minOrderAmount, Integer seatTypeId, Integer comboId,
                           LocalDate expiresOn) {
    public VoucherOffer {
        if (voucherId == null || code == null || kind == null || discountType == null || discountValue == null
                || expiresOn == null) {
            throw new DomainException("Voucher offer is incomplete");
        }
    }
}
