package fu.mss301.booking;

import fu.mss301.booking.BookingServiceApplicationTests.MutableClock;
import fu.mss301.booking.application.booking.ApplyVoucherUseCase;
import fu.mss301.booking.application.booking.BookingQueryService;
import fu.mss301.booking.application.booking.BookingView;
import fu.mss301.booking.application.booking.ComboLine;
import fu.mss301.booking.application.booking.HoldSeatsUseCase;
import fu.mss301.booking.application.booking.ReplaceBookingCombosUseCase;
import fu.mss301.booking.application.booking.ShowtimeCancellationUseCase;
import fu.mss301.booking.application.booking.ShowtimeCompensationService;
import fu.mss301.booking.application.combo.ComboCommand;
import fu.mss301.booking.application.combo.ComboService;
import fu.mss301.booking.application.payment.HandlePayosWebhookUseCase;
import fu.mss301.booking.application.payment.InitiatePaymentUseCase;
import fu.mss301.booking.application.port.CinemaCatalog;
import fu.mss301.booking.application.port.MovieCatalog;
import fu.mss301.booking.application.port.PaymentGateway;
import fu.mss301.booking.application.port.PaymentGateway.CheckoutLink;
import fu.mss301.booking.application.port.PaymentGateway.LinkRequest;
import fu.mss301.booking.application.port.PaymentGateway.LinkState;
import fu.mss301.booking.application.port.PaymentGateway.LinkStatus;
import fu.mss301.booking.application.port.PaymentGateway.VerifiedPayment;
import fu.mss301.booking.application.port.SeatLayoutProvider;
import fu.mss301.booking.application.port.SeatLayoutProvider.PhysicalSeat;
import fu.mss301.booking.application.port.ShowtimeCatalog;
import fu.mss301.booking.application.port.ShowtimeCatalog.SeatTypePrice;
import fu.mss301.booking.application.port.ShowtimeCatalog.ShowtimeSnapshot;
import fu.mss301.booking.application.voucher.ValueVoucherIssuer;
import fu.mss301.booking.application.voucher.VoucherCommand;
import fu.mss301.booking.application.voucher.VoucherService;
import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;
import fu.mss301.booking.domain.voucher.VoucherKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Cancelled showtimes against a real MySQL database. Seat 101 costs 90,000 and seat 102 (VIP) 150,000. */
@SpringBootTest(properties = {
    "spring.datasource.url=${BOOKING_TEST_DB_URL}",
    "spring.datasource.username=${BOOKING_TEST_DB_USERNAME:booking}",
    "spring.datasource.password=${BOOKING_TEST_DB_PASSWORD:booking_test}",
    "eureka.client.enabled=false",
    "booking.scheduling.enabled=false",
    "booking.hold-ttl=PT5M"
})
@EnabledIfEnvironmentVariable(named = "BOOKING_TEST_DB_URL", matches = ".+")
class BookingShowtimeCancelledIntegrationTests {
    private static final AtomicLong IDS = new AtomicLong(System.nanoTime() % 1_000_000_000L + 900_000_000L);

    @Autowired private HoldSeatsUseCase holdSeats;
    @Autowired private InitiatePaymentUseCase initiatePayment;
    @Autowired private HandlePayosWebhookUseCase webhook;
    @Autowired private ReplaceBookingCombosUseCase replaceCombos;
    @Autowired private ApplyVoucherUseCase vouchers;
    @Autowired private ComboService comboService;
    @Autowired private VoucherService voucherService;
    @Autowired private ValueVoucherIssuer valueVouchers;
    @Autowired private ShowtimeCancellationUseCase cancellation;
    @Autowired private ShowtimeCompensationService compensation;
    @Autowired private BookingQueryService queries;
    @Autowired private MutableClock clock;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private ShowtimeCatalog showtimes;
    @MockitoBean private SeatLayoutProvider seatLayout;
    @MockitoBean private MovieCatalog movies;
    @MockitoBean private CinemaCatalog cinemas;
    @MockitoBean private PaymentGateway gateway;

    private Long showtimeId;
    private int user;
    private LocalDateTime start;

    @BeforeEach
    void setUp() {
        clock.reset();
        showtimeId = IDS.incrementAndGet();
        user = (int) (IDS.incrementAndGet() % Integer.MAX_VALUE);
        startsIn(Duration.ofDays(1), false);
        when(showtimes.getSeatPrices(showtimeId))
            .thenReturn(Map.of(2, new SeatTypePrice(77, 2, new BigDecimal("150000"))));
        when(movies.getMovie(8)).thenReturn(new MovieCatalog.MovieInfo("Dune", null));
        when(cinemas.cinemaName(2)).thenReturn("Rạp Hà Nội");
        when(cinemas.screenName(3)).thenReturn("Phòng chiếu 1");
        when(cinemas.seatTypeNames()).thenReturn(Map.of(1, "Ghế thường", 2, "Ghế VIP"));
        when(seatLayout.getSeats(3)).thenReturn(List.of(new PhysicalSeat(101, "A", 1, 1, true),
            new PhysicalSeat(102, "A", 2, 2, true), new PhysicalSeat(104, "A", 4, 1, true)));
        when(gateway.createLink(any())).thenAnswer(invocation -> {
            LinkRequest request = invocation.getArgument(0);
            return new CheckoutLink("link-" + request.orderCode(), "https://pay.payos.vn/web/" + request.orderCode());
        });
        when(gateway.getLinkState(anyLong())).thenReturn(new LinkState(LinkStatus.PENDING, 0, null, null));
    }

    @Test
    void aBookingWaitingForPaymentIsCancelledAndItsLinkClosed() {
        var booking = holdSeats.hold(user, showtimeId, List.of(101));
        assertEquals(Map.of("movie_id", 8, "cinema_id", 2),
            jdbc.queryForMap("SELECT movie_id, cinema_id FROM bookings WHERE id = ?", booking.id()),
            "A new ticket stores the movie and cinema ids");
        var payment = initiatePayment.initiate(user, booking.id());
        cancelShowtime();

        var result = cancellation.handle(showtimeId);
        assertEquals(1, result.cancelledPending());
        assertEquals(0, result.cancelledPaid());
        var after = queries.getBooking(user, false, booking.id());
        assertEquals(BookingStatus.CANCELLED, after.status());
        assertEquals("FAILED", after.payment().status());
        verify(gateway).cancelLink(eq(payment.orderCode()), anyString());

        pay(payment.orderCode(), 90000);
        assertEquals("REFUNDED", queries.getBooking(user, false, booking.id()).payment().status());
    }

    @Test
    void earlyNoticeGivesTheMoneyBackAsAComboVoucherAndAValueVoucher() {
        Integer combo = combo("85000");
        var booking = holdSeats.hold(user, showtimeId, List.of(101, 102));
        replaceCombos.replace(user, booking.id(), List.of(new ComboLine(combo, 2)));
        var payment = payInFull(booking.id());
        cancelShowtime();

        assertEquals(1, cancellation.handle(showtimeId).cancelledPaid());
        var after = queries.getBooking(user, false, booking.id());
        assertEquals(BookingStatus.SHOWTIME_CANCELLED, after.status());
        assertNotNull(after.showtimeCancelledAt());
        assertTrue(after.seats().stream().allMatch(s -> s.status().name().equals("RELEASED")));
        assertEquals("REFUNDED", after.payment().status());
        assertEquals(Map.of("SHOWTIME_COMBO_REFUND", "COMBO_DISCOUNT 170000.00", "SHOWTIME_REFUND", "VALUE 240000.00"),
            issued(booking.id()));
        assertEquals(List.of("170000.00", "240000.00"), refundAmounts(payment));
        assertNotNull(jdbc.queryForObject("select compensated_at from bookings where id = ?", LocalDateTime.class,
            booking.id()));
        var mine = voucherService.mine(user);
        assertEquals(2, mine.size());
        assertTrue(mine.stream().allMatch(v -> v.endDate().equals(LocalDate.now(clock).plusDays(89))));
    }

    @Test
    void shortNoticeAlsoGivesAFreeTicketAndTheDearestComboAndReturnsUsedVouchers() {
        startsIn(Duration.ofMinutes(90), false);
        Integer popcorn = combo("85000");
        Integer drink = combo("40000");
        var booking = holdSeats.hold(user, showtimeId, List.of(101, 102));
        replaceCombos.replace(user, booking.id(), List.of(new ComboLine(popcorn, 1), new ComboLine(drink, 2)));
        String used = ownedVoucher(VoucherKind.TICKET_EXCHANGE);
        vouchers.apply(user, booking.id(), used, 101);
        var payment = payInFull(booking.id());
        assertEquals("USED", claimStatus(used));
        cancelShowtime();

        cancellation.handle(showtimeId);
        assertEquals("AVAILABLE", claimStatus(used));
        // Paid: 150,000 seat and 165,000 of combos.
        assertEquals(Map.of("SHOWTIME_COMBO_REFUND", "COMBO_DISCOUNT 165000.00", "SHOWTIME_REFUND", "VALUE 150000.00",
            "APOLOGY_TICKET", "TICKET_EXCHANGE 100.00", "APOLOGY_COMBO", "COMBO_DISCOUNT 85000.00"), issued(booking.id()));
        assertEquals(List.of("165000.00", "150000.00"), refundAmounts(payment));
    }

    @Test
    void aShowtimeAlreadyStartedCountsAsShortNotice() {
        startsIn(Duration.ofMinutes(10), false);
        var booking = holdSeats.hold(user, showtimeId, List.of(101));
        payInFull(booking.id());
        clock.advance(Duration.ofMinutes(30));
        cancelShowtime();
        cancellation.handle(showtimeId);
        assertTrue(issued(booking.id()).containsKey("APOLOGY_TICKET"));
        assertFalse(issued(booking.id()).containsKey("APOLOGY_COMBO"));
    }

    @Test
    void handlingAgainOrAtTheSameTimeNeverCompensatesTwice() throws Exception {
        var booking = holdSeats.hold(user, showtimeId, List.of(101));
        var payment = payInFull(booking.id());
        cancelShowtime();
        var barrier = new CyclicBarrier(4);
        Callable<Integer> run = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            return cancellation.handle(showtimeId).cancelledPaid();
        };
        int paid = 0;
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (var result : executor.invokeAll(Collections.nCopies(4, run))) paid += result.get();
        }
        assertEquals(1, paid);
        compensation.compensate(booking.id());
        assertEquals(0, cancellation.handle(showtimeId).cancelledPaid());
        assertEquals(1, jdbc.queryForObject("select count(*) from vouchers where source_booking_id = ?", Integer.class,
            booking.id()));
        assertEquals(List.of("90000.00"), refundAmounts(payment));
    }

    @Test
    void aShowtimeThatIsNotCancelledChangesNothing() {
        var booking = holdSeats.hold(user, showtimeId, List.of(101));
        payInFull(booking.id());
        assertThrows(InvalidStateTransitionException.class, () -> cancellation.handle(showtimeId));
        assertEquals(BookingStatus.CONFIRMED, queries.getBooking(user, false, booking.id()).status());
    }

    @Test
    void theJobCompensatesABookingWhoseEventWasLost() {
        var booking = holdSeats.hold(user, showtimeId, List.of(101));
        payInFull(booking.id());
        jdbc.update("update bookings set status = 'SHOWTIME_CANCELLED', showtime_cancelled_at = now(6) where id = ?",
            booking.id());
        jdbc.update("update booking_seats set status = 'RELEASED' where booking_id = ?", booking.id());

        compensation.compensatePending(1000);
        assertEquals(Map.of("SHOWTIME_REFUND", "VALUE 90000.00"), issued(booking.id()));
        assertNotNull(jdbc.queryForObject("select compensated_at from bookings where id = ?", LocalDateTime.class,
            booking.id()));
    }

    @Test
    void aBookingVouchersPaidGetsItsVouchersBackAndNoMoney() {
        String free = ownedVoucher(VoucherKind.TICKET_EXCHANGE);
        var booking = holdSeats.hold(user, showtimeId, List.of(101));
        vouchers.apply(user, booking.id(), free, 101);
        assertEquals("NOT_REQUIRED", initiatePayment.initiate(user, booking.id()).status());
        cancelShowtime();

        cancellation.handle(showtimeId);
        assertEquals("AVAILABLE", claimStatus(free));
        assertEquals(Map.of(), issued(booking.id()));
        assertEquals(BookingStatus.SHOWTIME_CANCELLED, queries.getBooking(user, false, booking.id()).status());
    }

    @Test
    void whatAValueVoucherCoveredComesBackAsValueAndTheVoucherStaysUsed() {
        var value = valueVouchers.forPayment(latePayment(), user, new BigDecimal("50000"));
        var booking = holdSeats.hold(user, showtimeId, List.of(101));
        vouchers.apply(user, booking.id(), value.code().value(), null);
        var payment = payInFull(booking.id());
        cancelShowtime();

        cancellation.handle(showtimeId);
        assertEquals("USED", claimStatus(value.code().value()));
        assertEquals(Map.of("SHOWTIME_REFUND", "VALUE 90000.00"), issued(booking.id()));
        assertEquals(List.of("40000.00"), refundAmounts(payment));
    }

    private Integer latePayment() {
        var booking = holdSeats.hold(user, showtimeId, List.of(104));
        var payment = initiatePayment.initiate(user, booking.id());
        jdbc.update("update payment_transactions set status = 'SUCCESS', paid_at = now(6) where id = ?", payment.id());
        jdbc.update("update bookings set status = 'CANCELLED' where id = ?", booking.id());
        jdbc.update("update booking_seats set status = 'RELEASED' where booking_id = ?", booking.id());
        return payment.id();
    }

    private Integer combo(String price) {
        return comboService.create(new ComboCommand("Combo " + System.nanoTime(), null, new BigDecimal(price))).id();
    }

    private String ownedVoucher(VoucherKind kind) {
        var created = voucherService.create(new VoucherCommand(null, kind, null, null, LocalDate.now(clock).minusDays(1),
            LocalDate.now(clock).plusDays(30), null, null, null));
        voucherService.assign(created.id(), user);
        return created.code();
    }

    private fu.mss301.booking.application.payment.PaymentView payInFull(Integer bookingId) {
        var payment = initiatePayment.initiate(user, bookingId);
        pay(payment.orderCode(), payment.amount().longValue());
        assertEquals(BookingStatus.CONFIRMED, queries.getBooking(user, false, bookingId).status());
        return payment;
    }

    private void pay(long orderCode, long amount) {
        when(gateway.verifyWebhook(any())).thenReturn(
            new VerifiedPayment(orderCode, amount, true, "FT" + orderCode, LocalDateTime.now(clock)));
        webhook.handle(Map.of());
    }

    private void startsIn(Duration duration, boolean cancelled) {
        start = LocalDateTime.now(clock).plus(duration);
        when(showtimes.getShowtime(showtimeId)).thenReturn(new ShowtimeSnapshot(showtimeId, 8, 2, 3, start,
            start.plusHours(2), new BigDecimal("90000"), !cancelled, cancelled));
    }

    private void cancelShowtime() {
        when(showtimes.getShowtime(showtimeId)).thenReturn(new ShowtimeSnapshot(showtimeId, 8, 2, 3, start,
            start.plusHours(2), new BigDecimal("90000"), false, true));
    }

    /** reason -> "kind value" of the vouchers issued for a booking */
    private Map<String, String> issued(Integer bookingId) {
        var rows = jdbc.queryForList("select source_reason, concat(kind, ' ', discount_value) as v from vouchers "
            + "where source_booking_id = ?", bookingId);
        var map = new java.util.HashMap<String, String>();
        rows.forEach(row -> map.put((String) row.get("source_reason"), (String) row.get("v")));
        return map;
    }

    private List<String> refundAmounts(fu.mss301.booking.application.payment.PaymentView payment) {
        return jdbc.queryForList("select amount from refunds where payment_id = ? and method = 'VOUCHER' order by id",
            BigDecimal.class, payment.id()).stream().map(BigDecimal::toPlainString).toList();
    }

    private String claimStatus(String code) {
        return jdbc.queryForObject("select c.status from user_vouchers c join vouchers v on v.id = c.voucher_id "
            + "where v.code = ?", String.class, code);
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }
}
