package fu.mss301.booking.domain.voucher;

import fu.mss301.booking.domain.shared.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What a voucher gives and when it can be used. Exchange vouchers always cover a whole ticket or combo
 * serving, so they are stored as 100 percent.
 */
public record VoucherTerms(VoucherKind kind, DiscountType discountType, BigDecimal discountValue, LocalDate startDate,
                           LocalDate endDate, BigDecimal minOrderAmount, Integer seatTypeId, Integer comboId) {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public VoucherTerms {
        if (kind == null) throw new DomainException("Voucher kind is required");
        if (startDate == null || endDate == null) throw new DomainException("Voucher start and end dates are required");
        if (endDate.isBefore(startDate)) throw new DomainException("Voucher end date cannot be before its start date");

        if (kind.isExchange()) {
            boolean otherType = discountType != null && discountType != DiscountType.PERCENT;
            boolean otherValue = discountValue != null && discountValue.compareTo(HUNDRED) != 0;
            if (otherType || otherValue) throw new DomainException("An exchange voucher always covers 100 percent");
            discountType = DiscountType.PERCENT;
            discountValue = HUNDRED;
        }
        requireValidDiscount(discountType, discountValue);
        discountValue = discountValue.setScale(2);

        if (minOrderAmount != null) {
            if (!kind.isDiscount()) throw new DomainException("Only discount vouchers can have a minimum order amount");
            requireWholeAmount(minOrderAmount, "Minimum order amount", BigDecimal.ZERO);
            minOrderAmount = minOrderAmount.setScale(2);
        }
        if (seatTypeId != null && !kind.appliesToTickets()) {
            throw new DomainException("Only ticket vouchers can be limited to a seat type");
        }
        if (kind == VoucherKind.COMBO_EXCHANGE && comboId == null) {
            throw new DomainException("A combo exchange voucher must name its combo");
        }
        if (kind != VoucherKind.COMBO_EXCHANGE && comboId != null) {
            throw new DomainException("Only combo exchange vouchers can name a combo");
        }
    }

    private static void requireValidDiscount(DiscountType type, BigDecimal value) {
        if (type == null || value == null) throw new DomainException("Voucher discount type and value are required");
        if (type == DiscountType.PERCENT) {
            if (value.compareTo(BigDecimal.ONE) < 0 || value.compareTo(HUNDRED) > 0
                    || value.stripTrailingZeros().scale() > 0) {
                throw new DomainException("A percent discount must be a whole number from 1 to 100");
            }
            return;
        }
        requireWholeAmount(value, "Discount amount", BigDecimal.ONE);
    }

    private static void requireWholeAmount(BigDecimal amount, String label, BigDecimal min) {
        // PayOS only accepts whole VND, so amounts stay whole.
        if (amount.compareTo(min) < 0 || amount.compareTo(MAX_AMOUNT) > 0 || amount.stripTrailingZeros().scale() > 0) {
            throw new DomainException(label + " must be a whole VND amount from " + min.toPlainString()
                + " to " + MAX_AMOUNT.toPlainString());
        }
    }
}
