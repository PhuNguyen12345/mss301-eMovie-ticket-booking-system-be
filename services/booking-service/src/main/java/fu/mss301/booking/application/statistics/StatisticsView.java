package fu.mss301.booking.application.statistics;

import java.time.LocalDate;
import java.util.List;

/** Sales of the showtimes between {@code from} and {@code to}, both included. */
public record StatisticsView(LocalDate from, LocalDate to, StatisticsGroup groupBy, StatisticsFigures total,
                             List<StatisticsRow> rows) {}
