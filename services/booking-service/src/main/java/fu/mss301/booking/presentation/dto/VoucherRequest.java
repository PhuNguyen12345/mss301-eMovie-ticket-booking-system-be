package fu.mss301.booking.presentation.dto;

import fu.mss301.booking.application.voucher.VoucherCommand;
import fu.mss301.booking.domain.voucher.DiscountType;
import fu.mss301.booking.domain.voucher.VoucherKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VoucherRequest(
    @Size(max = 50) String code,
    @NotNull VoucherKind kind,
    DiscountType discountType,
    BigDecimal discountValue,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    BigDecimal minOrderAmount,
    @Positive Integer seatTypeId,
    @Positive Integer comboId
) {
    public VoucherCommand toCommand() {
        return new VoucherCommand(code, kind, discountType, discountValue, startDate, endDate, minOrderAmount,
            seatTypeId, comboId);
    }
}
