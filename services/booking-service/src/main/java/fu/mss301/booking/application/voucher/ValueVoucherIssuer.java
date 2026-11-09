package fu.mss301.booking.application.voucher;

import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.domain.voucher.Voucher;
import fu.mss301.booking.domain.voucher.VoucherCode;
import fu.mss301.booking.domain.voucher.VoucherCodeTakenException;
import fu.mss301.booking.domain.voucher.VoucherRepository;
import fu.mss301.booking.domain.voucher.VoucherSourceReason;
import fu.mss301.booking.domain.voucher.VoucherTerms;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Issues vouchers the system owes straight to their owner. Each source gets at most one voucher, so calling again
 * for the same payment, or the same booking and reason, returns the voucher already issued; a retry never issues
 * twice.
 */
public class ValueVoucherIssuer {
    private final VoucherRepository vouchers;
    private final Transactions transactions;
    private final Duration validFor;
    private final Clock clock;

    /** @param validFor how long the voucher can be used, counted in whole days from today */
    public ValueVoucherIssuer(VoucherRepository vouchers, Transactions transactions, Duration validFor, Clock clock) {
        if (validFor == null || validFor.toDays() < 1) throw new IllegalArgumentException("Value vouchers must last a day or more");
        this.vouchers = vouchers;
        this.transactions = transactions;
        this.validFor = validFor;
        this.clock = clock;
    }

    /** For money received for a booking that could not be confirmed with it. */
    public Voucher forPayment(Integer paymentId, Integer ownerId, BigDecimal amount) {
        return issueOnce(() -> vouchers.findBySourcePaymentId(paymentId),
            now -> Voucher.forPayment(VoucherCode.generate(), ownerId, amount, paymentId, lastDay(now), now));
    }

    /** For what a booking's value voucher did not use. */
    public Voucher forRemainder(Integer bookingId, Integer ownerId, BigDecimal amount) {
        return issueOnce(() -> vouchers.findBySource(bookingId, VoucherSourceReason.REMAINDER),
            now -> Voucher.forRemainder(VoucherCode.generate(), ownerId, amount, bookingId, lastDay(now), now));
    }

    /**
     * For something the system owes the owner of a booking. One voucher per booking and reason.
     *
     * @param terms builds the voucher's terms from its first and last day
     */
    public Voucher forBooking(Integer bookingId, VoucherSourceReason reason, Integer ownerId,
                              BiFunction<LocalDate, LocalDate, VoucherTerms> terms) {
        return issueOnce(() -> vouchers.findBySource(bookingId, reason),
            now -> Voucher.forBooking(VoucherCode.generate(), ownerId, terms.apply(now.toLocalDate(), lastDay(now)),
                bookingId, reason, now));
    }

    private Voucher issueOnce(Supplier<Optional<Voucher>> existing,
                             Function<LocalDateTime, Voucher> issue) {
        try {
            return transactions.write(() -> existing.get()
                .orElseGet(() -> vouchers.save(issue.apply(LocalDateTime.now(clock)))));
        } catch (VoucherCodeTakenException exception) {
            // Another run issued it first; a clash of generated codes is too rare to retry.
            return transactions.read(existing).orElseThrow(() -> exception);
        }
    }

    /** The voucher is usable for {@code validFor} days including today. */
    private LocalDate lastDay(LocalDateTime now) {
        return now.toLocalDate().plusDays(validFor.toDays() - 1);
    }
}
