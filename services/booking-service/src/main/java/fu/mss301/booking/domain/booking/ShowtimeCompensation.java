package fu.mss301.booking.domain.booking;

import fu.mss301.booking.domain.voucher.VoucherKind;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * What a customer gets back when the cinema cancels the showtime of a paid booking. Money comes back as vouchers:
 * the combo part as a combo voucher, the rest, plus what any value voucher covered, as a value voucher. At short
 * notice the customer also gets a free ticket and, if they bought combos, a combo worth the dearest one.
 *
 * @param comboRefund  money paid for combos, given back as a combo voucher; zero when nothing
 * @param valueRefund  everything else the customer paid, given back as a value voucher; zero when nothing
 * @param moneyRefund  the part of {@code valueRefund} that was money, not an earlier value voucher
 * @param freeTicket   whether to give a free ticket
 * @param apologyCombo the value of the apology combo voucher; zero when none
 */
public record ShowtimeCompensation(BigDecimal comboRefund, BigDecimal valueRefund, BigDecimal moneyRefund,
                                   boolean freeTicket, BigDecimal apologyCombo) {
    private static final Set<VoucherKind> COMBO_KINDS = Set.of(VoucherKind.COMBO_EXCHANGE, VoucherKind.COMBO_DISCOUNT);

    /**
     * @param paid        money received for the booking through its payment, zero when vouchers paid it all
     * @param shortNotice whether the showtime was cancelled at short notice
     */
    public static ShowtimeCompensation of(Booking booking, BigDecimal paid, boolean shortNotice) {
        BigDecimal comboTotal = booking.combos().stream().map(BookingCombo::lineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal comboVouchers = discountOf(booking, COMBO_KINDS);
        BigDecimal comboPaid = whole(comboTotal.subtract(comboVouchers).max(BigDecimal.ZERO));
        BigDecimal comboRefund = comboPaid.min(whole(paid));
        BigDecimal moneyRefund = whole(paid).subtract(comboRefund);
        BigDecimal valueRefund = moneyRefund.add(whole(discountOf(booking, Set.of(VoucherKind.VALUE))));
        BigDecimal apologyCombo = shortNotice ? booking.combos().stream().map(BookingCombo::price)
            .max(BigDecimal::compareTo).map(ShowtimeCompensation::whole).orElse(BigDecimal.ZERO) : BigDecimal.ZERO;
        return new ShowtimeCompensation(comboRefund, valueRefund, moneyRefund, shortNotice, apologyCombo);
    }

    private static BigDecimal discountOf(Booking booking, Set<VoucherKind> kinds) {
        return booking.vouchers().stream().filter(voucher -> kinds.contains(voucher.kind()))
            .map(BookingVoucher::discountAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Vouchers hold whole VND. */
    private static BigDecimal whole(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.DOWN);
    }
}
