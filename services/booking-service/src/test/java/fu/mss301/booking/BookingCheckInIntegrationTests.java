package fu.mss301.booking;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import fu.mss301.booking.BookingServiceApplicationTests.MutableClock;
import fu.mss301.booking.application.booking.BookingQueryService;
import fu.mss301.booking.application.booking.CheckInUseCase;
import fu.mss301.booking.application.booking.HoldSeatsUseCase;
import fu.mss301.booking.application.booking.TicketQrUseCase;
import fu.mss301.booking.application.exception.BookingNotFoundException;
import fu.mss301.booking.application.exception.TicketAlreadyUsedException;
import fu.mss301.booking.application.payment.HandlePayosWebhookUseCase;
import fu.mss301.booking.application.payment.InitiatePaymentUseCase;
import fu.mss301.booking.application.port.CinemaCatalog;
import fu.mss301.booking.application.port.MovieCatalog;
import fu.mss301.booking.application.port.PaymentGateway;
import fu.mss301.booking.application.port.PaymentGateway.CheckoutLink;
import fu.mss301.booking.application.port.PaymentGateway.LinkRequest;
import fu.mss301.booking.application.port.PaymentGateway.VerifiedPayment;
import fu.mss301.booking.application.port.SeatLayoutProvider;
import fu.mss301.booking.application.port.SeatLayoutProvider.PhysicalSeat;
import fu.mss301.booking.application.port.ShowtimeCatalog;
import fu.mss301.booking.application.port.ShowtimeCatalog.ShowtimeSnapshot;
import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;
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

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Duration;
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

/** Checking tickets in at the door against a real MySQL database. The showtime starts 10 minutes from now. */
@SpringBootTest(properties = {
    "spring.datasource.url=${BOOKING_TEST_DB_URL}",
    "spring.datasource.username=${BOOKING_TEST_DB_USERNAME:booking}",
    "spring.datasource.password=${BOOKING_TEST_DB_PASSWORD:booking_test}",
    "eureka.client.enabled=false",
    "booking.scheduling.enabled=false",
    "booking.hold-ttl=PT5M"
})
@EnabledIfEnvironmentVariable(named = "BOOKING_TEST_DB_URL", matches = ".+")
class BookingCheckInIntegrationTests {
    private static final AtomicLong IDS = new AtomicLong(System.nanoTime() % 1_000_000_000L + 700_000_000L);
    private static final int STAFF = 1;

    @Autowired private HoldSeatsUseCase holdSeats;
    @Autowired private InitiatePaymentUseCase initiatePayment;
    @Autowired private HandlePayosWebhookUseCase webhook;
    @Autowired private CheckInUseCase checkIn;
    @Autowired private TicketQrUseCase ticketQr;
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

    @BeforeEach
    void setUp() {
        clock.reset();
        showtimeId = IDS.incrementAndGet();
        user = (int) (IDS.incrementAndGet() % Integer.MAX_VALUE);
        startsIn(Duration.ofMinutes(10));
        when(showtimes.getSeatPrices(showtimeId)).thenReturn(Map.of());
        when(movies.getMovie(8)).thenReturn(new MovieCatalog.MovieInfo("Dune", null));
        when(cinemas.cinemaName(2)).thenReturn("Rạp Hà Nội");
        when(cinemas.screenName(3)).thenReturn("Phòng chiếu 1");
        when(cinemas.seatTypeNames()).thenReturn(Map.of(1, "Ghế thường"));
        when(seatLayout.getSeats(3)).thenReturn(List.of(
            new PhysicalSeat(101, "A", 1, 1, true), new PhysicalSeat(102, "A", 2, 1, true),
            new PhysicalSeat(103, "A", 3, 1, true)));
        when(gateway.createLink(any())).thenAnswer(invocation -> {
            LinkRequest request = invocation.getArgument(0);
            return new CheckoutLink("link-" + request.orderCode(), "https://pay.payos.vn/web/" + request.orderCode());
        });
    }

    @Test
    void aGroupGoesInAFewAtATimeAndThenTheCodeIsUsed() {
        String code = paid(101, 102, 103);
        var first = checkIn.checkIn(STAFF, code.toLowerCase(), 2);
        assertEquals(2, first.admittedCount());
        assertEquals(1, first.remaining());
        assertEquals("Dune", first.movieTitle());
        assertEquals(3, first.seats().size());
        var rest = checkIn.checkIn(STAFF + 1, code, null);
        assertEquals(3, rest.admittedCount());
        assertEquals(0, rest.remaining());

        var used = assertThrows(TicketAlreadyUsedException.class, () -> checkIn.checkIn(STAFF, code, null));
        assertEquals(List.of(2, 1), used.booking().checkIns().stream().map(s -> s.quantity()).toList());
        assertEquals(List.of(STAFF, STAFF + 1), jdbc.queryForList(
            "select checked_in_by from booking_check_ins c join bookings b on b.id = c.booking_id "
                + "where b.booking_code = ? order by c.id", Integer.class, code));
        assertEquals(3, queries.getBooking(user, false, bookingId(code)).admittedCount());
    }

    @Test
    void twoScannersLettingEveryoneInAtOnceAdmitOnce() throws Exception {
        String code = paid(101, 102);
        assertEquals(1, winners(8, () -> checkIn.checkIn(STAFF, code, null)));
        assertEquals(2, jdbc.queryForObject("select sum(quantity) from booking_check_ins c join bookings b "
            + "on b.id = c.booking_id where b.booking_code = ?", Integer.class, code));
    }

    @Test
    void scannersLettingOnePersonInEachNeverPassTheSeats() throws Exception {
        String code = paid(101, 102);
        assertEquals(2, winners(5, () -> checkIn.checkIn(STAFF, code, 1)));
        assertEquals(2, checkIn(code).admittedCount());
    }

    @Test
    void theDoorIsClosedTooEarlyAndTooLate() {
        startsIn(Duration.ofMinutes(30));
        String code = paid(101);
        assertThrows(InvalidStateTransitionException.class, () -> checkIn.checkIn(STAFF, code, null));
        clock.advance(Duration.ofMinutes(15));
        assertEquals(1, checkIn.checkIn(STAFF, code, null).admittedCount());

        String late = paid(102);
        clock.advance(Duration.ofMinutes(36));
        assertThrows(InvalidStateTransitionException.class, () -> checkIn.checkIn(STAFF, late, null));
    }

    @Test
    void onlyPaidBookingsAndKnownCodesCheckIn() {
        var pending = holdSeats.hold(user, showtimeId, List.of(101));
        assertThrows(InvalidStateTransitionException.class,
            () -> checkIn.checkIn(STAFF, pending.bookingCode(), null));
        assertThrows(BookingNotFoundException.class, () -> checkIn.checkIn(STAFF, "BK000000000000NOPE00", null));
    }

    @Test
    void aBookingWithoutATicketCopyAsksShowtimeServiceForTheStart() {
        String code = paid(101);
        jdbc.update("update bookings set movie_title = null, start_time = null, end_time = null, cinema_name = null, "
            + "screen_name = null where booking_code = ?", code);
        startsIn(Duration.ofHours(3));
        assertThrows(InvalidStateTransitionException.class, () -> checkIn.checkIn(STAFF, code, null));
        startsIn(Duration.ofMinutes(5));
        var view = checkIn.checkIn(STAFF, code, null);
        assertEquals(1, view.admittedCount());
        assertNull(view.movieTitle());
    }

    @Test
    void theTicketQrHoldsTheBookingCodeForItsOwner() throws Exception {
        String code = paid(101);
        Integer id = bookingId(code);
        assertEquals(code, decode(ticketQr.png(user, false, id)));
        assertEquals(code, decode(ticketQr.png(STAFF, true, id)));
        assertThrows(BookingNotFoundException.class, () -> ticketQr.png(user + 1, false, id));
        var pending = holdSeats.hold(user, showtimeId, List.of(102));
        assertThrows(InvalidStateTransitionException.class, () -> ticketQr.png(user, false, pending.id()));
    }

    private fu.mss301.booking.application.booking.CheckInView checkIn(String code) {
        return assertThrows(TicketAlreadyUsedException.class, () -> checkIn.checkIn(STAFF, code, 1)).booking();
    }

    private static long winners(int threads, Runnable scan) throws Exception {
        var barrier = new CyclicBarrier(threads);
        Callable<Boolean> attempt = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            try {
                scan.run();
                return true;
            } catch (TicketAlreadyUsedException exception) {
                return false;
            }
        };
        long wins = 0;
        try (var executor = Executors.newFixedThreadPool(threads)) {
            for (var result : executor.invokeAll(Collections.nCopies(threads, attempt))) if (result.get()) wins++;
        }
        return wins;
    }

    private String paid(int... seatIds) {
        var booking = holdSeats.hold(user, showtimeId, java.util.Arrays.stream(seatIds).boxed().toList());
        var payment = initiatePayment.initiate(user, booking.id());
        when(gateway.verifyWebhook(any())).thenReturn(new VerifiedPayment(payment.orderCode(),
            payment.amount().longValue(), true, "FT" + payment.orderCode(), LocalDateTime.now(clock)));
        webhook.handle(Map.of());
        assertEquals(BookingStatus.CONFIRMED, queries.getBooking(user, false, booking.id()).status());
        return booking.bookingCode();
    }

    private void startsIn(Duration duration) {
        LocalDateTime start = LocalDateTime.now(clock).plus(duration);
        when(showtimes.getShowtime(showtimeId)).thenReturn(
            new ShowtimeSnapshot(showtimeId, 8, 2, 3, start, start.plusHours(2), new BigDecimal("90000"), true));
    }

    private Integer bookingId(String code) {
        return jdbc.queryForObject("select id from bookings where booking_code = ?", Integer.class, code);
    }

    private static String decode(byte[] png) throws Exception {
        var image = ImageIO.read(new ByteArrayInputStream(png));
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        var bitmap = new BinaryBitmap(new HybridBinarizer(
            new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels)));
        return new QRCodeReader().decode(bitmap).getText();
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
