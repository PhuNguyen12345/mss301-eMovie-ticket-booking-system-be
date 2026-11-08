package fu.mss301.booking.application.port;

import fu.mss301.booking.application.statistics.StatisticsGroup;
import fu.mss301.booking.application.statistics.StatisticsRow;

import java.time.LocalDateTime;
import java.util.List;

/** Aggregate sales queries; they read tables directly and never load aggregates. */
public interface BookingStatistics {
    /**
     * Groups that have any booking or refund among showtimes starting in {@code [from, until)}.
     * Dates are ordered by date, movies and cinemas by revenue, highest first.
     */
    List<StatisticsRow> summarize(LocalDateTime from, LocalDateTime until, StatisticsGroup group);
}
