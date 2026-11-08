package fu.mss301.booking.application.voucher;

import fu.mss301.booking.domain.voucher.ClaimStatus;
import fu.mss301.booking.domain.voucher.DiscountType;
import fu.mss301.booking.domain.voucher.Voucher;
import fu.mss301.booking.domain.voucher.VoucherKind;
import fu.mss301.booking.domain.voucher.VoucherStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The admin's view of a voucher.
 *
 * @param sourcePaymentId for a value voucher, the payment it gives back
 * @param sourceBookingId for a value voucher, the booking whose value voucher it continues
 */
public record VoucherView(Integer id, String code, VoucherKind kind, DiscountType discountType,
                          BigDecimal discountValue, LocalDate startDate, LocalDate endDate, BigDecimal minOrderAmount,
                          Integer seatTypeId, Integer comboId, VoucherStatus status, Integer ownerId,
                          ClaimStatus claimStatus, Integer sourcePaymentId, Integer sourceBookingId) {
    public static VoucherView of(Voucher voucher) {
        var terms = voucher.terms();
        var claim = voucher.claim();
        return new VoucherView(voucher.id(), voucher.code().value(), terms.kind(), terms.discountType(),
            terms.discountValue(), terms.startDate(), terms.endDate(), terms.minOrderAmount(), terms.seatTypeId(),
            terms.comboId(), voucher.status(), claim == null ? null : claim.userId(),
            claim == null ? null : claim.status(), voucher.sourcePaymentId(), voucher.sourceBookingId());
    }
}
