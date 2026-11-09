package fu.mss301.booking.domain.payment;

import fu.mss301.booking.domain.shared.DomainException;

import java.math.BigDecimal;

/** Part of a payment given back as one voucher. */
public record VoucherShare(Integer voucherId, BigDecimal amount) {
    public VoucherShare {
        if (voucherId == null) throw new DomainException("Voucher id is required");
        if (amount == null || amount.signum() <= 0) throw new DomainException("A voucher share must be positive");
    }
}
