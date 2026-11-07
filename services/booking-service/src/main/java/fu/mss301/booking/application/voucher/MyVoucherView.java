package fu.mss301.booking.application.voucher;

import fu.mss301.booking.domain.voucher.DiscountType;
import fu.mss301.booking.domain.voucher.Voucher;
import fu.mss301.booking.domain.voucher.VoucherAvailability;
import fu.mss301.booking.domain.voucher.VoucherKind;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A voucher as its owner sees it. */
public record MyVoucherView(Integer id, String code, VoucherKind kind, DiscountType discountType,
                            BigDecimal discountValue, LocalDate startDate, LocalDate endDate, BigDecimal minOrderAmount,
                            Integer seatTypeId, Integer comboId, VoucherAvailability status) {
    public static MyVoucherView of(Voucher voucher, LocalDate today) {
        var terms = voucher.terms();
        return new MyVoucherView(voucher.id(), voucher.code().value(), terms.kind(), terms.discountType(),
            terms.discountValue(), terms.startDate(), terms.endDate(), terms.minOrderAmount(), terms.seatTypeId(),
            terms.comboId(), voucher.availabilityOn(today));
    }
}
