package fu.mss301.booking.domain.booking;

import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.voucher.DiscountType;
import fu.mss301.booking.domain.voucher.VoucherKind;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Works out what each voucher takes off a booking. Vouchers are priced in a fixed order: ticket exchange, ticket
 * discount on the seats left, combo exchange, combo discount on the combo amount left, then the value voucher on
 * whatever is still to pay. So the total never goes below zero.
 */
final class VoucherPricing {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final List<VoucherKind> ORDER = List.of(VoucherKind.TICKET_EXCHANGE, VoucherKind.TICKET_DISCOUNT,
        VoucherKind.COMBO_EXCHANGE, VoucherKind.COMBO_DISCOUNT, VoucherKind.VALUE);

    /**
     * @param kept    vouchers that still apply, in their original order
     * @param amounts what each kept voucher takes off
     * @param dropped vouchers that no longer apply (only when not strict)
     */
    record Result(List<BookingVoucher> kept, Map<BookingVoucher, BigDecimal> amounts, List<BookingVoucher> dropped,
                  BigDecimal discount) {}

    private final Map<Integer, BookingSeat> seats;
    private final Map<Integer, BookingCombo> combos;
    private final boolean strict;
    private final Map<BookingVoucher, BigDecimal> amounts = new IdentityHashMap<>();
    private final List<BookingVoucher> dropped = new ArrayList<>();
    private final Set<Integer> exchangedSeats = new HashSet<>();
    private final Map<Integer, Integer> exchangedServings = new HashMap<>();

    private VoucherPricing(List<BookingSeat> seats, List<BookingCombo> combos, boolean strict) {
        this.seats = seats.stream().collect(Collectors.toMap(BookingSeat::seatId, Function.identity()));
        this.combos = combos.stream().collect(Collectors.toMap(BookingCombo::comboId, Function.identity()));
        this.strict = strict;
    }

    /**
     * @param strict true rejects the whole list when one voucher does not apply; false drops that voucher instead
     */
    static Result price(List<BookingSeat> seats, List<BookingCombo> combos, List<BookingVoucher> vouchers,
                        boolean strict) {
        return new VoucherPricing(seats, combos, strict).run(vouchers);
    }

    private Result run(List<BookingVoucher> vouchers) {
        for (VoucherKind kind : ORDER) {
            for (BookingVoucher voucher : vouchers) {
                if (voucher.kind() == kind) price(voucher);
            }
        }
        var kept = vouchers.stream().filter(amounts::containsKey).toList();
        var discount = kept.stream().map(amounts::get).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Result(kept, amounts, List.copyOf(dropped), discount);
    }

    private void price(BookingVoucher voucher) {
        String problem;
        BigDecimal amount = BigDecimal.ZERO;
        switch (voucher.kind()) {
            case TICKET_EXCHANGE -> {
                BookingSeat seat = seats.get(voucher.seatId());
                problem = ticketExchangeProblem(voucher, seat);
                if (problem == null) amount = seat.price();
            }
            case TICKET_DISCOUNT -> {
                BigDecimal base = seats.values().stream()
                    .filter(seat -> !exchangedSeats.contains(seat.seatId()) && matchesSeatType(voucher, seat))
                    .map(BookingSeat::price).reduce(BigDecimal.ZERO, BigDecimal::add);
                problem = discountProblem(voucher, base);
                if (problem == null) amount = discountOf(voucher, base);
            }
            case COMBO_EXCHANGE -> {
                BookingCombo combo = combos.get(voucher.comboId());
                int used = exchangedServings.getOrDefault(voucher.comboId(), 0);
                problem = combo == null ? "This booking has no combo this voucher is for"
                    : used >= combo.quantity() ? "Every serving of this combo already has a voucher" : null;
                if (problem == null) amount = combo.price();
            }
            case COMBO_DISCOUNT -> {
                BigDecimal base = combos.values().stream().map(BookingCombo::lineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).subtract(comboExchanged());
                problem = discountProblem(voucher, base);
                if (problem == null) amount = discountOf(voucher, base);
            }
            case VALUE -> {
                problem = onlyOne(voucher);
                if (problem == null) amount = voucher.discountValue().min(left());
            }
            default -> throw new IllegalStateException("Unknown voucher kind " + voucher.kind());
        }
        if (problem == null && amount.signum() <= 0) problem = "Nothing is left for this voucher to discount";
        if (problem != null) {
            if (strict) throw new DomainException(problem);
            dropped.add(voucher);
            return;
        }
        amounts.put(voucher, amount);
        switch (voucher.kind()) {
            case TICKET_EXCHANGE -> exchangedSeats.add(voucher.seatId());
            case COMBO_EXCHANGE -> exchangedServings.merge(voucher.comboId(), 1, Integer::sum);
            default -> { }
        }
    }

    private String ticketExchangeProblem(BookingVoucher voucher, BookingSeat seat) {
        if (seat == null) return "Seat " + voucher.seatId() + " is not part of this booking";
        if (exchangedSeats.contains(seat.seatId())) return "Seat " + seat.seatId() + " already has a voucher";
        if (!matchesSeatType(voucher, seat)) return "This voucher is for a different seat type";
        return null;
    }

    private String discountProblem(BookingVoucher voucher, BigDecimal base) {
        String one = onlyOne(voucher);
        if (one != null) return one;
        if (voucher.minOrderAmount() != null && base.compareTo(voucher.minOrderAmount()) < 0) {
            return "The booking does not reach the minimum amount of " + voucher.minOrderAmount().toPlainString()
                + " for this voucher";
        }
        return null;
    }

    private String onlyOne(BookingVoucher voucher) {
        boolean taken = amounts.keySet().stream().anyMatch(other -> other.kind() == voucher.kind());
        return taken ? "A booking can use only one " + voucher.kind() + " voucher" : null;
    }

    private static boolean matchesSeatType(BookingVoucher voucher, BookingSeat seat) {
        return voucher.seatTypeId() == null || Objects.equals(voucher.seatTypeId(), seat.seatTypeId());
    }

    /** Percent discounts round down to whole VND, because PayOS only takes whole amounts. */
    private static BigDecimal discountOf(BookingVoucher voucher, BigDecimal base) {
        if (voucher.discountType() == DiscountType.FIXED) return voucher.discountValue().min(base);
        return base.multiply(voucher.discountValue()).divide(HUNDRED, 0, RoundingMode.DOWN);
    }

    private BigDecimal comboExchanged() {
        return amounts.entrySet().stream().filter(entry -> entry.getKey().kind() == VoucherKind.COMBO_EXCHANGE)
            .map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal left() {
        BigDecimal subtotal = seats.values().stream().map(BookingSeat::price).reduce(BigDecimal.ZERO, BigDecimal::add);
        subtotal = combos.values().stream().map(BookingCombo::lineTotal).reduce(subtotal, BigDecimal::add);
        return amounts.values().stream().reduce(subtotal, BigDecimal::subtract);
    }
}
