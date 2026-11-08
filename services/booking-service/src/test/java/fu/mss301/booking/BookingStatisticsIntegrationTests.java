package fu.mss301.booking;

import fu.mss301.booking.application.booking.TicketBackfillService;
import fu.mss301.booking.application.exception.UpstreamServiceException;
import fu.mss301.booking.application.port.CinemaCatalog;
import fu.mss301.booking.application.port.MovieCatalog;
import fu.mss301.booking.application.port.ShowtimeCatalog;
import fu.mss301.booking.application.port.ShowtimeCatalog.ShowtimeSnapshot;
import fu.mss301.booking.application.statistics.StatisticsFigures;
import fu.mss301.booking.application.statistics.StatisticsGroup;
import fu.mss301.booking.application.statistics.StatisticsQuery;
import fu.mss301.booking.application.statistics.StatisticsRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Statistics and the ticket backfill against a real MySQL database. Every run uses showtime dates in its own
 * far-future month, so rows left by other tests or earlier runs never fall in the range.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=${BOOKING_TEST_DB_URL}",
    "spring.datasource.username=${BOOKING_TEST_DB_USERNAME:booking}",
    "spring.datasource.password=${BOOKING_TEST_DB_PASSWORD:booking_test}",
    "eureka.client.enabled=false",
    "booking.scheduling.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "BOOKING_TEST_DB_URL", matches = ".+")
class BookingStatisticsIntegrationTests {
    private static final AtomicLong IDS = new AtomicLong(System.nanoTime() % 1_000_000_000L + 1_900_000_000L);
    private static final int MOVIE_A = 1001;
    private static final int MOVIE_B = 1002;
    private static final int CINEMA_X = 11;
    private static final int CINEMA_Y = 12;

    @Autowired private StatisticsQuery statistics;
    @Autowired private TicketBackfillService backfill;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private ShowtimeCatalog showtimes;
    @MockitoBean private MovieCatalog movies;
    @MockitoBean private CinemaCatalog cinemas;

    /** Jan 31 of a year no other run uses, so the day after starts a new month. */
    private LocalDate day;
    private Integer combo;

    @BeforeEach
    void setUp() {
        day = LocalDate.of(2200 + (int) (IDS.incrementAndGet() % 7000), 1, 31);
        combo = insert("INSERT INTO combos (name, price, status, created_at) VALUES (?, 85000, 'ACTIVE', NOW(6))",
            "QA stats " + IDS.incrementAndGet());
    }

    @Test
    void countsOnlyConfirmedBookingsByShowtimeDateMonthMovieAndCinema() {
        fixture();

        var byDay = statistics.summarize(day, day.plusDays(1), StatisticsGroup.DAY);
        assertEquals(List.of(
            row(day.toString(), day.toString(), 3, 4, 420000, 170000, 74000, 516000, 220000),
            row(day.plusDays(1).toString(), day.plusDays(1).toString(), 1, 1, 90000, 0, 90000, 0, 0)), byDay.rows());
        var total = figures(4, 5, 510000, 170000, 164000, 516000, 220000);
        assertEquals(total, byDay.total());
        assertEquals(total.revenue(), total.ticketAmount().add(total.comboAmount()).subtract(total.discountAmount()),
            "Ticket and combo amounts less discounts add up to the revenue");

        var byMonth = statistics.summarize(day, day.plusDays(1), StatisticsGroup.MONTH);
        String january = day.toString().substring(0, 7);
        String february = day.plusDays(1).toString().substring(0, 7);
        assertEquals(List.of(row(january, january, 3, 4, 420000, 170000, 74000, 516000, 220000),
            row(february, february, 1, 1, 90000, 0, 90000, 0, 0)), byMonth.rows());
        assertEquals(total, byMonth.total());

        var byMovie = statistics.summarize(day, day.plusDays(1), StatisticsGroup.MOVIE);
        assertEquals(List.of(
            row("1001", "Phim A", 2, 3, 330000, 170000, 114000, 386000, 90000),
            row(null, "Không rõ", 1, 1, 90000, 0, 0, 90000, 0),
            row("1002", "Phim B", 1, 1, 90000, 0, 50000, 40000, 130000)), byMovie.rows());
        assertEquals(total, byMovie.total());

        var byCinema = statistics.summarize(day, day.plusDays(1), StatisticsGroup.CINEMA);
        assertEquals(List.of(
            row("11", "Rạp X", 1, 2, 240000, 170000, 24000, 386000, 180000),
            row(null, "Không rõ", 1, 1, 90000, 0, 0, 90000, 0),
            row("12", "Rạp Y", 2, 2, 180000, 0, 140000, 40000, 40000)), byCinema.rows());
        assertEquals(total, byCinema.total());
    }

    @Test
    void theRangeIncludesBothDaysAndNothingElse() {
        fixture();
        var firstDay = statistics.summarize(day, day, StatisticsGroup.DAY);
        assertEquals(1, firstDay.rows().size());
        assertEquals(516000, firstDay.total().revenue().intValueExact());
        var secondDay = statistics.summarize(day.plusDays(1), day.plusDays(1), StatisticsGroup.DAY);
        assertEquals(1, secondDay.total().bookings());
        var empty = statistics.summarize(day.plusDays(3), day.plusDays(9), StatisticsGroup.MOVIE);
        assertEquals(List.of(), empty.rows());
        assertEquals(StatisticsFigures.ZERO, empty.total());
    }

    @Test
    void backfillCompletesOldTicketsWithoutChangingWhatTheyShow() {
        long noTicket = IDS.incrementAndGet();
        long oldTicket = IDS.incrementAndGet();
        long unreachable = IDS.incrementAndGet();
        LocalDateTime start = day.atTime(19, 0);
        when(showtimes.getShowtime(noTicket)).thenReturn(snapshot(noTicket, MOVIE_A, CINEMA_X, start));
        when(showtimes.getShowtime(oldTicket)).thenReturn(snapshot(oldTicket, MOVIE_B, CINEMA_Y, start));
        when(showtimes.getShowtime(unreachable)).thenThrow(new UpstreamServiceException("Showtime service is down"));
        when(movies.getMovie(MOVIE_A)).thenReturn(new MovieCatalog.MovieInfo("Phim A mới", "https://img/a.jpg"));
        when(movies.getMovie(MOVIE_B)).thenReturn(new MovieCatalog.MovieInfo("Phim B mới", null));
        when(cinemas.cinemaName(CINEMA_X)).thenReturn("Rạp X");
        when(cinemas.cinemaName(CINEMA_Y)).thenReturn("Rạp Y mới");
        when(cinemas.screenName(3)).thenReturn("Phòng 3");

        int first = oldBooking(noTicket, null);
        int second = oldBooking(oldTicket, "Phim B cũ");
        int third = oldBooking(unreachable, null);
        int fourth = oldBooking(noTicket, null);

        assertTrue(backfill.fillMissing(2) >= 3, "Pages past a booking that cannot be filled");

        var filled = jdbc.queryForMap("SELECT * FROM bookings WHERE id = ?", first);
        assertEquals("Phim A mới", filled.get("movie_title"));
        assertEquals("https://img/a.jpg", filled.get("movie_poster_url"));
        assertEquals("Rạp X", filled.get("cinema_name"));
        assertEquals("Phòng 3", filled.get("screen_name"));
        assertEquals(start, filled.get("start_time"));
        assertEquals(start.plusHours(2), filled.get("end_time"));
        assertEquals(MOVIE_A, filled.get("movie_id"));
        assertEquals(CINEMA_X, filled.get("cinema_id"));
        assertEquals(MOVIE_A, jdbc.queryForObject("SELECT movie_id FROM bookings WHERE id = ?", Integer.class, fourth));

        var kept = jdbc.queryForMap("SELECT * FROM bookings WHERE id = ?", second);
        assertEquals("Phim B cũ", kept.get("movie_title"), "A sold ticket keeps its title");
        assertEquals("Rạp cũ", kept.get("cinema_name"));
        assertEquals(day.atTime(9, 0), kept.get("start_time"));
        assertEquals(MOVIE_B, kept.get("movie_id"));
        assertEquals(CINEMA_Y, kept.get("cinema_id"));

        var skipped = jdbc.queryForMap("SELECT movie_title, movie_id FROM bookings WHERE id = ?", third);
        assertNull(skipped.get("movie_title"));
        assertNull(skipped.get("movie_id"));
        verify(showtimes, times(1)).getShowtime(noTicket);

        Object version = kept.get("version");
        backfill.fillMissing(2);
        assertEquals(version, jdbc.queryForObject("SELECT version FROM bookings WHERE id = ?", Integer.class, second),
            "A complete ticket is not written again");
    }

    /**
     * Showtime dates are {@code day}, {@code day + 1} and the days around them. Seats cost 90,000 and the VIP seat
     * 150,000; every combo line is 2 x 85,000.
     */
    private void fixture() {
        LocalDateTime d = day.atStartOfDay();
        // Ticket discount: 240,000 + 170,000 - 24,000 = 386,000.
        int b1 = booking("CONFIRMED", d, MOVIE_A, "Phim A", CINEMA_X, "Rạp X", "386000", "24000");
        seat(b1, 1, "90000", "BOOKED");
        seat(b1, 2, "150000", "BOOKED");
        combo(b1);
        payment(b1, "386000", "SUCCESS");
        // Value voucher, and a late second payment given back as a voucher.
        int b2 = booking("CONFIRMED", d.plusDays(1).minusNanos(1000), MOVIE_B, "Phim B", CINEMA_Y, "Rạp Y", "40000",
            "50000");
        seat(b2, 1, "90000", "BOOKED");
        payment(b2, "40000", "SUCCESS");
        payment(b2, "40000", "REFUNDED");
        // Paid entirely with a voucher.
        int b3 = booking("CONFIRMED", d.plusDays(1).plusHours(10), MOVIE_A, "Phim A", CINEMA_Y, "Rạp Y", "0", "90000");
        seat(b3, 1, "90000", "BOOKED");
        // Not counted, only their refunds are.
        int b4 = booking("SHOWTIME_CANCELLED", d.plusHours(12), MOVIE_A, "Phim A", CINEMA_X, "Rạp X", "90000", "0");
        seat(b4, 1, "90000", "RELEASED");
        payment(b4, "90000", "REFUNDED");
        int b5 = booking("EXPIRED", d.plusHours(12), MOVIE_B, "Phim B", CINEMA_X, "Rạp X", "90000", "0");
        seat(b5, 1, "90000", "RELEASED");
        payment(b5, "90000", "REFUNDED");
        int b6 = booking("PENDING_PAYMENT", d.plusHours(12), MOVIE_A, "Phim A", CINEMA_X, "Rạp X", "90000", "0");
        seat(b6, 1, "90000", "HELD");
        payment(b6, "90000", "PENDING");
        int b7 = booking("CANCELLED", d.plusHours(12), MOVIE_A, "Phim A", CINEMA_X, "Rạp X", "90000", "0");
        seat(b7, 1, "90000", "RELEASED");
        payment(b7, "90000", "FAILED");
        // Ids not filled in yet.
        int b9 = booking("CONFIRMED", d.plusHours(15), null, "Phim A", null, "Rạp X", "90000", "0");
        seat(b9, 1, "90000", "BOOKED");
        payment(b9, "90000", "SUCCESS");
        // Outside the range on either side.
        for (LocalDateTime outside : List.of(d.minusNanos(1000), d.plusDays(2))) {
            int b = booking("CONFIRMED", outside, MOVIE_A, "Phim A", CINEMA_X, "Rạp X", "90000", "0");
            seat(b, 1, "90000", "BOOKED");
            payment(b, "90000", "SUCCESS");
        }
    }

    private int booking(String status, LocalDateTime start, Integer movie, String title, Integer cinema,
                        String cinemaName, String total, String discount) {
        long showtime = IDS.incrementAndGet();
        return insert("""
            INSERT INTO bookings (user_id, showtime_id, movie_id, cinema_id, booking_code, booking_time, total_amount,
                discount_amount, status, expired_at, created_at, updated_at, movie_title, cinema_name, screen_name,
                start_time, end_time)
            VALUES (7, ?, ?, ?, ?, NOW(6), ?, ?, ?, NOW(6), NOW(6), NOW(6), ?, ?, 'Phòng 3', ?, ?)
            """, showtime, movie, cinema, "BKST" + showtime, new BigDecimal(total), new BigDecimal(discount), status,
            title, cinemaName, Timestamp.valueOf(start), Timestamp.valueOf(start.plusHours(2)));
    }

    /** A booking made before tickets stored ids; {@code title} null when it has no ticket at all. */
    private int oldBooking(long showtime, String title) {
        LocalDateTime start = title == null ? null : day.atTime(9, 0);
        return insert("""
            INSERT INTO bookings (user_id, showtime_id, booking_code, booking_time, total_amount, discount_amount,
                status, expired_at, created_at, updated_at, movie_title, cinema_name, screen_name, start_time, end_time)
            VALUES (7, ?, ?, NOW(6), 90000, 0, 'CONFIRMED', NOW(6), NOW(6), NOW(6), ?, ?, ?, ?, ?)
            """, showtime, "BKOLD" + IDS.incrementAndGet(), title, title == null ? null : "Rạp cũ",
            title == null ? null : "Phòng cũ", start == null ? null : Timestamp.valueOf(start),
            start == null ? null : Timestamp.valueOf(start.plusHours(2)));
    }

    private void seat(int booking, int seat, String price, String status) {
        jdbc.update("""
            INSERT INTO booking_seats (booking_id, showtime_id, seat_id, price, status, created_at, updated_at)
            SELECT id, showtime_id, ?, ?, ?, NOW(6), NOW(6) FROM bookings WHERE id = ?
            """, seat, new BigDecimal(price), status, booking);
    }

    private void combo(int booking) {
        jdbc.update("INSERT INTO booking_combos (booking_id, combo_id, quantity, price, combo_name) VALUES (?, ?, 2, 85000, 'Bắp')",
            booking, combo);
    }

    private void payment(int booking, String amount, String status) {
        jdbc.update("""
            INSERT INTO payment_transactions (booking_id, payment_method, provider, order_code, amount, status,
                created_at, updated_at)
            VALUES (?, 'PAYOS', 'PAYOS', ?, ?, ?, NOW(6), NOW(6))
            """, booking, IDS.incrementAndGet(), new BigDecimal(amount), status);
    }

    private int insert(String sql, Object... args) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, keys);
        return keys.getKey().intValue();
    }

    private static ShowtimeSnapshot snapshot(long id, int movie, int cinema, LocalDateTime start) {
        return new ShowtimeSnapshot(id, movie, cinema, 3, start, start.plusHours(2), new BigDecimal("90000"), true);
    }

    private static StatisticsRow row(String key, String label, long bookings, long tickets, long ticketAmount,
                                     long comboAmount, long discount, long revenue, long refunded) {
        return new StatisticsRow(key, label, figures(bookings, tickets, ticketAmount, comboAmount, discount, revenue,
            refunded));
    }

    private static StatisticsFigures figures(long bookings, long tickets, long ticketAmount, long comboAmount,
                                             long discount, long revenue, long refunded) {
        return new StatisticsFigures(bookings, tickets, BigDecimal.valueOf(ticketAmount),
            BigDecimal.valueOf(comboAmount), BigDecimal.valueOf(discount), BigDecimal.valueOf(revenue),
            BigDecimal.valueOf(refunded));
    }
}
