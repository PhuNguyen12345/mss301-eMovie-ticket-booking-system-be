package fu.mss301.booking.application.statistics;

/**
 * One group of the statistics.
 *
 * @param key   {@code 2026-11-08} by day, {@code 2026-11} by month, the movie or cinema id otherwise; null for
 *              bookings whose movie or cinema is not known yet
 * @param label what to show: the key for dates, the name on the ticket for a movie or cinema
 */
public record StatisticsRow(String key, String label, StatisticsFigures figures) {}
