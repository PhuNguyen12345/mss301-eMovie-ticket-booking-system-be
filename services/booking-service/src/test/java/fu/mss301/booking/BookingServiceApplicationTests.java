package fu.mss301.booking;

import fu.mss301.booking.application.booking.BookingQueryService;
import fu.mss301.booking.application.booking.CancelBookingUseCase;
import fu.mss301.booking.application.booking.ExpireOverdueBookingsUseCase;
import fu.mss301.booking.application.booking.HoldSeatsUseCase;
import fu.mss301.booking.application.booking.SeatMapQuery;
import fu.mss301.booking.application.booking.SeatMapView.Availability;
import fu.mss301.booking.application.exception.SeatUnavailableException;
import fu.mss301.booking.application.payment.HandlePayosWebhookUseCase;
import fu.mss301.booking.application.payment.InitiatePaymentUseCase;
import fu.mss301.booking.application.payment.RefundAdminUseCase;
import fu.mss301.booking.application.port.PaymentGateway;
import fu.mss301.booking.application.port.PaymentGateway.CheckoutLink;
import fu.mss301.booking.application.port.PaymentGateway.LinkState;
import fu.mss301.booking.application.port.PaymentGateway.LinkStatus;
import fu.mss301.booking.application.port.PaymentGateway.VerifiedPayment;
import fu.mss301.booking.application.port.SeatLayoutProvider;
import fu.mss301.booking.application.port.SeatLayoutProvider.PhysicalSeat;
import fu.mss301.booking.application.port.ShowtimeCatalog;
import fu.mss301.booking.application.port.ShowtimeCatalog.SeatTypePrice;
import fu.mss301.booking.application.port.ShowtimeCatalog.ShowtimeSnapshot;
import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.domain.booking.SeatHoldStatus;
import fu.mss301.booking.domain.payment.PaymentStatus;
import fu.mss301.booking.domain.payment.RefundStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

@SpringBootTest(properties = {
    "spring.datasource.url=${BOOKING_TEST_DB_URL}",
    "spring.datasource.username=${BOOKING_TEST_DB_USERNAME:booking}",
    "spring.datasource.password=${BOOKING_TEST_DB_PASSWORD:booking_test}",
    "eureka.client.enabled=false",
    "booking.scheduling.enabled=false",
    "booking.hold-ttl=PT5M"
})
@EnabledIfEnvironmentVariable(named = "BOOKING_TEST_DB_URL", matches = ".+")
class BookingServiceApplicationTests {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final AtomicLong SHOWTIME_IDS = new AtomicLong(System.nanoTime() % 1_000_000_000L);

    @Autowired private HoldSeatsUseCase holdSeats;
    @Autowired private SeatMapQuery seatMap;
    @Autowired private BookingQueryService queries;
    @Autowired private CancelBookingUseCase cancelBooking;
    @Autowired private ExpireOverdueBookingsUseCase expireOverdue;
    @Autowired private InitiatePaymentUseCase initiatePayment;
    @Autowired private HandlePayosWebhookUseCase webhook;
    @Autowired private RefundAdminUseCase refunds;
    @Autowired private MutableClock clock;

    @MockitoBean private ShowtimeCatalog showtimes;
    @MockitoBean private SeatLayoutProvider seatLayout;
    @MockitoBean private PaymentGateway gateway;

    private Long showtimeId;

    @BeforeEach
    void setUp() {
        clock.reset();
        showtimeId = SHOWTIME_IDS.incrementAndGet();
        LocalDateTime start = LocalDateTime.now(clock).plusDays(1);
        when(showtimes.getShowtime(showtimeId))
            .thenReturn(new ShowtimeSnapshot(showtimeId, 3, start, new BigDecimal("90000"), true));
        when(showtimes.getSeatPrices(showtimeId))
            .thenReturn(Map.of(2, new SeatTypePrice(77, 2, new BigDecimal("150000"))));
        when(seatLayout.getSeats(3)).thenReturn(List.of(
            new PhysicalSeat(101, "A", 1, 1, true),
            new PhysicalSeat(102, "A", 2, 2, true),
            new PhysicalSeat(103, "A", 3, 1, false)));
        when(gateway.createLink(any())).thenAnswer(invocation -> {
            PaymentGateway.LinkRequest request = invocation.getArgument(0);
            return new CheckoutLink("link-" + request.orderCode(), "https://pay.payos.vn/web/" + request.orderCode());
        });
        when(gateway.getLinkState(anyLong())).thenReturn(new LinkState(LinkStatus.PENDING, 0, null, null));
    }

    @Test
    void concurrentHoldsOfTheSameSeatLetExactlyOneWin() throws Exception {
        var barrier = new CyclicBarrier(8);
        Callable<Boolean> attempt = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            try {
                holdSeats.hold(1000 + (int) Thread.currentThread().threadId() % 1000, showtimeId, List.of(101));
                return true;
            } catch (SeatUnavailableException exception) {
                return false;
            }
        };
        try (var executor = Executors.newFixedThreadPool(8)) {
            var results = executor.invokeAll(java.util.Collections.nCopies(8, attempt));
            long winners = 0;
            for (var result : results) if (result.get()) winners++;
            assertEquals(1, winners);
        }
        assertEquals(Availability.HELD, availability(101));
    }

    @Test
    void holdPricesBySeatTypeAndRejectsUnusableSeats() {
        var booking = holdSeats.hold(7, showtimeId, List.of(101, 102));
        assertEquals(new BigDecimal("240000.00"), booking.totalAmount());
        assertThrows(SeatUnavailableException.class, () -> holdSeats.hold(7, showtimeId, List.of(103)));
        assertEquals(Availability.UNAVAILABLE, availability(103));
    }

    @Test
    void paidWebhookConfirmsBookingIdempotently() {
        var booking = holdSeats.hold(7, showtimeId, List.of(101));
        var payment = initiatePayment.initiate(7, booking.id());
        assertEquals(booking.id() * 100L + 1, payment.orderCode());
        assertEquals(payment, initiatePayment.initiate(7, booking.id()), "pending link is re-used");
        verify(gateway, times(1)).createLink(any());

        when(gateway.verifyWebhook(any())).thenReturn(
            new VerifiedPayment(payment.orderCode(), 90000, true, "FT1", LocalDateTime.now(clock)));
        webhook.handle(Map.of());
        webhook.handle(Map.of());

        var confirmed = queries.getBooking(7, false, booking.id());
        assertEquals(BookingStatus.CONFIRMED, confirmed.status());
        assertEquals(SeatHoldStatus.BOOKED, confirmed.seats().getFirst().status());
        assertEquals(PaymentStatus.SUCCESS, confirmed.payment().status());
        assertEquals(Availability.BOOKED, availability(101));
        assertTrue(refunds.list(RefundStatus.PENDING).stream().noneMatch(r -> r.bookingId().equals(booking.id())));
    }

    @Test
    void expiryReleasesSeatsAndLatePaymentIsFlaggedForRefund() {
        var booking = holdSeats.hold(7, showtimeId, List.of(101));
        var payment = initiatePayment.initiate(7, booking.id());

        clock.advance(Duration.ofMinutes(6));
        expireOverdue.expireOverdue(1000);

        var expired = queries.getBooking(7, false, booking.id());
        assertEquals(BookingStatus.EXPIRED, expired.status());
        assertEquals(PaymentStatus.FAILED, expired.payment().status());
        verify(gateway).cancelLink(eq(payment.orderCode()), anyString());
        assertEquals(Availability.AVAILABLE, availability(101));
        assertNotNull(holdSeats.hold(8, showtimeId, List.of(101)), "released seat can be held again");

        when(gateway.verifyWebhook(any())).thenReturn(
            new VerifiedPayment(payment.orderCode(), 90000, true, "FT2", LocalDateTime.now(clock)));
        webhook.handle(Map.of());

        assertEquals(BookingStatus.EXPIRED, queries.getBooking(7, false, booking.id()).status());
        var refund = refunds.list(RefundStatus.PENDING).stream()
            .filter(r -> r.bookingId().equals(booking.id())).findFirst().orElseThrow();
        var completed = refunds.complete(refund.id());
        assertEquals(RefundStatus.COMPLETED, completed.status());
        assertEquals(PaymentStatus.REFUNDED, queries.getBooking(7, false, booking.id()).payment().status());
    }

    @Test
    void expiryJobConfirmsWhenPayosSaysPaidButWebhookWasLost() {
        var booking = holdSeats.hold(7, showtimeId, List.of(101));
        var payment = initiatePayment.initiate(7, booking.id());
        when(gateway.getLinkState(payment.orderCode()))
            .thenReturn(new LinkState(LinkStatus.PAID, 90000, "FT3", LocalDateTime.now(clock)));

        clock.advance(Duration.ofMinutes(6));
        expireOverdue.expireOverdue(1000);

        assertEquals(BookingStatus.CONFIRMED, queries.getBooking(7, false, booking.id()).status());
    }

    @Test
    void cancelReleasesSeatsAndOnlyOwnerSeesBooking() {
        var booking = holdSeats.hold(7, showtimeId, List.of(102));
        assertThrows(fu.mss301.common.exception.ApiException.class, () -> cancelBooking.cancel(8, booking.id()));
        assertEquals(BookingStatus.CANCELLED, cancelBooking.cancel(7, booking.id()).status());
        assertEquals(Availability.AVAILABLE, availability(102));
        assertThrows(fu.mss301.common.exception.ApiException.class, () -> queries.getBooking(8, false, booking.id()));
        assertNotNull(queries.getBooking(8, true, booking.id()));
    }

    private Availability availability(int seatId) {
        return seatMap.seatMap(showtimeId).seats().stream().filter(s -> s.seatId() == seatId)
            .findFirst().orElseThrow().status();
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        private volatile Duration offset = Duration.ZERO;

        void advance(Duration duration) {
            offset = offset.plus(duration);
        }

        void reset() {
            offset = Duration.ZERO;
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return Instant.now().plus(offset);
        }
    }
}
