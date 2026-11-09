package fu.mss301.booking.application.statistics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Sales of one group. Only confirmed bookings count, so for each of them
 * {@code ticketAmount + comboAmount - discountAmount = revenue}.
 *
 * @param bookings       confirmed bookings
 * @param tickets        booked seats of those bookings
 * @param ticketAmount   seat prices before discounts
 * @param comboAmount    combos, quantity times unit price, before discounts
 * @param discountAmount what vouchers took off, value vouchers included
 * @param revenue        money received through successful payments
 * @param refundedAmount payments given back as vouchers, of any booking in the group; not part of revenue
 */
public record StatisticsFigures(long bookings, long tickets, BigDecimal ticketAmount, BigDecimal comboAmount,
                                BigDecimal discountAmount, BigDecimal revenue, BigDecimal refundedAmount) {
    public static final StatisticsFigures ZERO = new StatisticsFigures(0, 0, money(BigDecimal.ZERO),
        money(BigDecimal.ZERO), money(BigDecimal.ZERO), money(BigDecimal.ZERO), money(BigDecimal.ZERO));

    public StatisticsFigures {
        ticketAmount = money(ticketAmount);
        comboAmount = money(comboAmount);
        discountAmount = money(discountAmount);
        revenue = money(revenue);
        refundedAmount = money(refundedAmount);
    }

    public StatisticsFigures plus(StatisticsFigures other) {
        return new StatisticsFigures(bookings + other.bookings, tickets + other.tickets,
            ticketAmount.add(other.ticketAmount), comboAmount.add(other.comboAmount),
            discountAmount.add(other.discountAmount), revenue.add(other.revenue),
            refundedAmount.add(other.refundedAmount));
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.UNNECESSARY);
    }
}
