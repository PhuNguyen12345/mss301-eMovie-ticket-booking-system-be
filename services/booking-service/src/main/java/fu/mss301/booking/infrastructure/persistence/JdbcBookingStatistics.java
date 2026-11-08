package fu.mss301.booking.infrastructure.persistence;

import fu.mss301.booking.application.port.BookingStatistics;
import fu.mss301.booking.application.statistics.StatisticsFigures;
import fu.mss301.booking.application.statistics.StatisticsGroup;
import fu.mss301.booking.application.statistics.StatisticsRow;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JdbcBookingStatistics implements BookingStatistics {
    /**
     * One row per booking first, each child table summed in its own subquery, so a booking with several seats,
     * combos and payments is never multiplied by a join.
     */
    private static final String PER_BOOKING = """
        SELECT b.status = 'CONFIRMED' AS counted, b.start_time, b.movie_id, b.movie_title, b.cinema_id,
               b.cinema_name, b.discount_amount,
               (SELECT COUNT(*) FROM booking_seats s WHERE s.booking_id = b.id AND s.status = 'BOOKED') AS tickets,
               (SELECT COALESCE(SUM(s.price), 0) FROM booking_seats s
                 WHERE s.booking_id = b.id AND s.status = 'BOOKED') AS ticket_amount,
               (SELECT COALESCE(SUM(c.quantity * c.price), 0) FROM booking_combos c
                 WHERE c.booking_id = b.id) AS combo_amount,
               (SELECT COALESCE(SUM(p.amount), 0) FROM payment_transactions p
                 WHERE p.booking_id = b.id AND p.status = 'SUCCESS') AS paid,
               (SELECT COALESCE(SUM(p.amount), 0) FROM payment_transactions p
                 WHERE p.booking_id = b.id AND p.status = 'REFUNDED') AS refunded
          FROM bookings b
         WHERE b.start_time >= :from AND b.start_time < :until
        """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcBookingStatistics(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<StatisticsRow> summarize(LocalDateTime from, LocalDateTime until, StatisticsGroup group) {
        String key = switch (group) {
            case DAY -> "DATE_FORMAT(x.start_time, '%Y-%m-%d')";
            case MONTH -> "DATE_FORMAT(x.start_time, '%Y-%m')";
            case MOVIE -> "CAST(x.movie_id AS CHAR)";
            case CINEMA -> "CAST(x.cinema_id AS CHAR)";
        };
        String label = switch (group) {
            case DAY, MONTH -> "MAX(" + key + ")";
            case MOVIE -> "MAX(x.movie_title)";
            case CINEMA -> "MAX(x.cinema_name)";
        };
        String order = switch (group) {
            case DAY, MONTH -> "group_key";
            case MOVIE, CINEMA -> "revenue DESC, group_key IS NULL, CAST(group_key AS UNSIGNED)";
        };
        String sql = """
            SELECT %s AS group_key, %s AS label,
                   SUM(x.counted) AS bookings,
                   SUM(IF(x.counted, x.tickets, 0)) AS tickets,
                   SUM(IF(x.counted, x.ticket_amount, 0)) AS ticket_amount,
                   SUM(IF(x.counted, x.combo_amount, 0)) AS combo_amount,
                   SUM(IF(x.counted, x.discount_amount, 0)) AS discount_amount,
                   SUM(IF(x.counted, x.paid, 0)) AS revenue,
                   SUM(x.refunded) AS refunded
              FROM (%s) x
             GROUP BY group_key
            HAVING bookings > 0 OR refunded > 0
             ORDER BY %s
            """.formatted(key, label, PER_BOOKING, order);
        var params = new MapSqlParameterSource().addValue("from", from).addValue("until", until);
        return jdbc.query(sql, params, (rs, row) -> {
            String groupKey = rs.getString("group_key");
            String name = groupKey == null ? "Không rõ" : rs.getString("label");
            return new StatisticsRow(groupKey, name, new StatisticsFigures(rs.getLong("bookings"),
                rs.getLong("tickets"), rs.getBigDecimal("ticket_amount"), rs.getBigDecimal("combo_amount"),
                rs.getBigDecimal("discount_amount"), rs.getBigDecimal("revenue"), rs.getBigDecimal("refunded")));
        });
    }
}
