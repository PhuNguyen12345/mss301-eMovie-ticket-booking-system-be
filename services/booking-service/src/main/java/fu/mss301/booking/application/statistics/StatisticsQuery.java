package fu.mss301.booking.application.statistics;

import fu.mss301.booking.application.port.BookingStatistics;
import fu.mss301.booking.application.port.Transactions;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Sales statistics for admins, by showtime date. */
public class StatisticsQuery {
    public static final int MAX_DAYS = 366;
    private final BookingStatistics statistics;
    private final Transactions transactions;

    public StatisticsQuery(BookingStatistics statistics, Transactions transactions) {
        this.statistics = statistics;
        this.transactions = transactions;
    }

    /** @param from first showtime date, included; @param to last showtime date, included */
    public StatisticsView summarize(LocalDate from, LocalDate to, StatisticsGroup group) {
        if (from == null || to == null) throw new IllegalArgumentException("from and to are required");
        if (from.isAfter(to)) throw new IllegalArgumentException("from must not be after to");
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new IllegalArgumentException("The range can cover at most " + MAX_DAYS + " days");
        }
        StatisticsGroup by = group == null ? StatisticsGroup.DAY : group;
        var rows = transactions.read(() -> statistics.summarize(from.atStartOfDay(), to.plusDays(1).atStartOfDay(), by));
        var total = rows.stream().map(StatisticsRow::figures).reduce(StatisticsFigures.ZERO, StatisticsFigures::plus);
        return new StatisticsView(from, to, by, total, rows);
    }
}
