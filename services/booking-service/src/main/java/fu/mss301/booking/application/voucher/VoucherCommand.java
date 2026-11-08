package fu.mss301.booking.application.voucher;

import fu.mss301.booking.domain.voucher.DiscountType;
import fu.mss301.booking.domain.voucher.VoucherKind;
import fu.mss301.booking.domain.voucher.VoucherTerms;

import java.math.BigDecimal;
import java.time.LocalDate;

/** @param code optional when creating, generated if empty; ignored when updating */
public record VoucherCommand(String code, VoucherKind kind, DiscountType discountType, BigDecimal discountValue,
                             LocalDate startDate, LocalDate endDate, BigDecimal minOrderAmount, Integer seatTypeId,
                             Integer comboId) {
    VoucherTerms terms() {
        return new VoucherTerms(kind, discountType, discountValue, startDate, endDate, minOrderAmount, seatTypeId, comboId);
    }
}
