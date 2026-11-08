package fu.mss301.booking;

import fu.mss301.booking.application.booking.BookingPage;
import fu.mss301.booking.application.payment.PaymentHistoryQuery;
import fu.mss301.booking.application.payment.PaymentHistoryView;
import fu.mss301.booking.domain.payment.PaymentStatus;
import fu.mss301.booking.domain.payment.RefundMethod;
import fu.mss301.booking.domain.payment.RefundStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.math.BigDecimal;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Admin payment history against a real MySQL database. Every test creates its payments on its own far-future day,
 * so rows left by other tests or earlier runs never fall in the range.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=${BOOKING_TEST_DB_URL}",
    "spring.datasource.username=${BOOKING_TEST_DB_USERNAME:booking}",
    "spring.datasource.password=${BOOKING_TEST_DB_PASSWORD:booking_test}",
    "eureka.client.enabled=false",
    "booking.scheduling.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "BOOKING_TEST_DB_URL", matches = ".+")
class PaymentHistoryIntegrationTests {
    private static final AtomicLong IDS = new AtomicLong(System.nanoTime() % 1_000_000_000L + 2_900_000_000L);

    @Autowired private PaymentHistoryQuery history;
    @Autowired private JdbcTemplate jdbc;

    private LocalDate day;
    private int user;

    @BeforeEach
    void setUp() {
        day = LocalDate.of(2200, 1, 1).plusDays(IDS.incrementAndGet() % 2_500_000L);
        user = (int) (IDS.incrementAndGet() % 1_000_000L) + 1_000_000;
    }

    @Test
    void listsEveryPaymentNewestFirstWithItsBookingAndRefunds() {
        LocalDateTime d = day.atStartOfDay();
        int booking = booking();
        int voucher = voucher();
        int first = payment(booking, "386000", "REFUNDED", d, "FT1");
        refund(first, "170000", "VOUCHER", voucher);
        refund(first, "216000", "VOUCHER", null);
        int second = payment(booking, "386000", "FAILED", d.plusHours(10), null);
        int sameTimeLow = payment(booking(), "90000", "PENDING", d.plusHours(12), null);
        int sameTimeHigh = payment(booking(), "90000", "SUCCESS", d.plusHours(12), "FT2");
        int lastMoment = payment(booking(), "40000", "SUCCESS", d.plusDays(1).minusNanos(1000), "FT3");
        payment(booking(), "40000", "SUCCESS", d.minusNanos(1000), "OUT1");
        payment(booking(), "40000", "SUCCESS", d.plusDays(1), "OUT2");

        var page = history.search(null, null, null, day, day, 0, 20);
        assertEquals(List.of(lastMoment, sameTimeHigh, sameTimeLow, second, first), ids(page),
            "Newest first, the higher id first on the same instant, only payments made that day");
        assertEquals(5, page.totalElements());

        var refunded = page.content().getLast();
        assertEquals(booking, refunded.bookingId());
        assertEquals(user, refunded.userId());
        assertEquals("BKPH" + booking, refunded.bookingCode());
        assertEquals(PaymentStatus.REFUNDED, refunded.status());
        assertEquals(0, new BigDecimal("386000").compareTo(refunded.amount()));
        assertEquals("FT1", refunded.providerTransactionId());
        assertEquals(d.plusMinutes(1), refunded.paidAt());
        assertEquals(d, refunded.createdAt());
        assertEquals(2, refunded.refunds().size());
        var line = refunded.refunds().getFirst();
        assertEquals(RefundMethod.VOUCHER, line.method());
        assertEquals(voucher, line.voucherId());
        assertEquals(RefundStatus.COMPLETED, line.status());
        assertEquals(0, new BigDecimal("170000").compareTo(line.amount()));
        assertEquals(0, new BigDecimal("386000").compareTo(refunded.refunds().stream()
            .map(PaymentHistoryView.RefundLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add)),
            "Refunds add up to the payment");
        assertTrue(page.content().get(1).refunds().isEmpty());
    }

    @Test
    void filtersByStatusBookingAndOrderCode() {
        LocalDateTime d = day.atStartOfDay();
        int booking = booking();
        int refunded = payment(booking, "386000", "REFUNDED", d, null);
        int failed = payment(booking, "386000", "FAILED", d.plusHours(1), null);
        int other = payment(booking(), "90000", "SUCCESS", d.plusHours(2), null);

        assertEquals(List.of(refunded), ids(history.search(PaymentStatus.REFUNDED, null, null, day, day, 0, 20)));
        assertEquals(List.of(other), ids(history.search(PaymentStatus.SUCCESS, null, null, day, day, 0, 20)));
        assertEquals(List.of(), ids(history.search(PaymentStatus.PENDING, null, null, day, day, 0, 20)));
        assertEquals(List.of(failed, refunded), ids(history.search(null, booking, null, null, null, 0, 20)));
        Long orderCode = jdbc.queryForObject("SELECT order_code FROM payment_transactions WHERE id = ?", Long.class,
            failed);
        assertEquals(List.of(failed), ids(history.search(null, null, orderCode, null, null, 0, 20)));
        assertEquals(List.of(failed), ids(history.search(PaymentStatus.FAILED, booking, orderCode, day, day, 0, 20)));
        assertEquals(List.of(), ids(history.search(PaymentStatus.SUCCESS, booking, null, day, day, 0, 20)));
    }

    @Test
    void pagesDoNotOverlap() {
        LocalDateTime d = day.atStartOfDay();
        for (int i = 0; i < 5; i++) payment(booking(), "90000", "SUCCESS", d.plusMinutes(i), null);
        var first = history.search(null, null, null, day, day, 0, 2);
        var second = history.search(null, null, null, day, day, 1, 2);
        var third = history.search(null, null, null, day, day, 2, 2);
        assertEquals(5, first.totalElements());
        assertEquals(3, first.totalPages());
        assertTrue(first.first());
        assertTrue(third.last());
        assertEquals(1, third.content().size());
        var seen = new HashSet<Integer>();
        for (var page : List.of(first, second, third)) seen.addAll(ids(page));
        assertEquals(5, seen.size());
    }

    private static List<Integer> ids(BookingPage<PaymentHistoryView> page) {
        return page.content().stream().map(PaymentHistoryView::id).toList();
    }

    private int booking() {
        long showtime = IDS.incrementAndGet();
        int id = insert("""
            INSERT INTO bookings (user_id, showtime_id, booking_code, booking_time, total_amount, discount_amount,
                status, expired_at, created_at, updated_at)
            VALUES (?, ?, ?, NOW(6), 90000, 0, 'CONFIRMED', NOW(6), NOW(6), NOW(6))
            """, user, showtime, "BKTMP" + showtime);
        jdbc.update("UPDATE bookings SET booking_code = ? WHERE id = ?", "BKPH" + id, id);
        return id;
    }

    private int payment(int booking, String amount, String status, LocalDateTime createdAt, String reference) {
        LocalDateTime paidAt = reference == null ? null : createdAt.plusMinutes(1);
        return insert("""
            INSERT INTO payment_transactions (booking_id, payment_method, provider, order_code, amount, status,
                provider_transaction_id, checkout_url, paid_at, created_at, updated_at)
            VALUES (?, 'BANK_TRANSFER', 'PAYOS', ?, ?, ?, ?, 'https://pay.payos.vn/web/x', ?, ?, ?)
            """, booking, IDS.incrementAndGet(), new BigDecimal(amount), status, reference,
            paidAt == null ? null : Timestamp.valueOf(paidAt), Timestamp.valueOf(createdAt),
            Timestamp.valueOf(createdAt));
    }

    private void refund(int payment, String amount, String method, Integer voucher) {
        jdbc.update("""
            INSERT INTO refunds (payment_id, amount, reason, method, voucher_id, status, refunded_at, created_at,
                updated_at)
            VALUES (?, ?, 'Showtime cancelled', ?, ?, 'COMPLETED', NOW(6), NOW(6), NOW(6))
            """, payment, new BigDecimal(amount), method, voucher);
    }

    private int voucher() {
        return insert("""
            INSERT INTO vouchers (code, kind, discount_type, discount_value, start_date, end_date, status, created_at)
            VALUES (?, 'VALUE', 'FIXED', 170000, CURDATE(), CURDATE(), 'ACTIVE', NOW(6))
            """, "QAPH" + IDS.incrementAndGet());
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
}
