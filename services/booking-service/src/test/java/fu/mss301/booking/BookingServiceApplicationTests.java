package fu.mss301.booking;

import fu.mss301.booking.application.booking.BookingPage;
import fu.mss301.booking.application.booking.BookingQueryService;
import fu.mss301.booking.application.booking.BookingSearch;
import fu.mss301.booking.application.booking.BookingView;
import fu.mss301.booking.application.booking.CancelBookingUseCase;
import fu.mss301.booking.application.booking.ComboLine;
import fu.mss301.booking.application.booking.ExpireOverdueBookingsUseCase;
import fu.mss301.booking.application.booking.HoldSeatsUseCase;
import fu.mss301.booking.application.booking.ReplaceBookingCombosUseCase;
import fu.mss301.booking.application.booking.SeatMapQuery;
import fu.mss301.booking.application.booking.SeatMapView.Availability;
import fu.mss301.booking.application.combo.ComboCommand;
import fu.mss301.booking.application.combo.ComboService;
import fu.mss301.booking.application.exception.BookingNotFoundException;
import fu.mss301.booking.application.exception.ComboUnavailableException;
import fu.mss301.booking.application.exception.PaymentGatewayException;
import fu.mss301.booking.application.exception.SeatUnavailableException;
import fu.mss301.booking.application.payment.HandlePayosWebhookUseCase;
import fu.mss301.booking.application.payment.InitiatePaymentUseCase;
import fu.mss301.booking.application.payment.RefundAdminUseCase;
import fu.mss301.booking.application.port.DomainEvents;
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
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.domain.booking.SeatHoldStatus;
import fu.mss301.booking.domain.booking.event.BookingCancelled;
import fu.mss301.booking.domain.combo.ComboStatus;
import fu.mss301.booking.domain.payment.PaymentStatus;
import fu.mss301.booking.domain.payment.RefundStatus;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
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
    @Autowired private ComboService comboService;
    @Autowired private ReplaceBookingCombosUseCase replaceCombos;
    @Autowired private BookingRepository repository;
    @Autowired private Transactions transactions;
    @Autowired private DomainEvents domainEvents;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;

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
        assertThrows(BookingNotFoundException.class, () -> cancelBooking.cancel(8, booking.id()));
        assertEquals(BookingStatus.CANCELLED, cancelBooking.cancel(7, booking.id()).status());
        assertEquals(Availability.AVAILABLE, availability(102));
        assertThrows(BookingNotFoundException.class, () -> queries.getBooking(8, false, booking.id()));
        assertNotNull(queries.getBooking(8, true, booking.id()));
    }

    @Test
    void childRowsKeepTheirIdsAcrossStateChanges() {
        var booking = holdSeats.hold(7, showtimeId, List.of(101, 102));
        var seatIds = seatRowIds(booking.id());
        assertEquals(2, seatIds.size());
        var payment = initiatePayment.initiate(7, booking.id());
        when(gateway.verifyWebhook(any())).thenReturn(
            new VerifiedPayment(payment.orderCode(), 240000, true, "FT4", LocalDateTime.now(clock)));
        webhook.handle(Map.of());
        assertEquals(seatIds, seatRowIds(booking.id()));

    }

    @Test
    void refundRowKeepsItsIdWhenCompleted() {
        var booking = holdSeats.hold(7, showtimeId, List.of(101));
        var payment = initiatePayment.initiate(7, booking.id());
        clock.advance(Duration.ofMinutes(6));
        expireOverdue.expireOverdue(1000);
        when(gateway.verifyWebhook(any())).thenReturn(
            new VerifiedPayment(payment.orderCode(), 90000, true, "FT5", LocalDateTime.now(clock)));
        webhook.handle(Map.of());

        var refund = refunds.list(RefundStatus.PENDING).stream()
            .filter(r -> r.bookingId().equals(booking.id())).findFirst().orElseThrow();
        assertNotNull(refund.id());
        assertEquals(refund.id(), refunds.complete(refund.id()).id());
        assertTrue(refunds.list(RefundStatus.COMPLETED).stream().anyMatch(r -> r.id().equals(refund.id())));
    }

    @Test
    void cancelFailsThePendingPaymentAndClosesItsLink() {
        var booking = holdSeats.hold(7, showtimeId, List.of(101));
        var payment = initiatePayment.initiate(7, booking.id());
        cancelBooking.cancel(7, booking.id());
        assertEquals(PaymentStatus.FAILED, queries.getBooking(7, false, booking.id()).payment().status());
        verify(gateway).cancelLink(eq(payment.orderCode()), anyString());
    }

    @Test
    void savingAStaleAggregateIsRejected() {
        var booking = holdSeats.hold(7, showtimeId, List.of(101));
        var stale = transactions.read(() -> repository.findById(booking.id())).orElseThrow();
        cancelBooking.cancel(7, booking.id());

        stale.cancelBy(7, LocalDateTime.now(clock));
        assertThrows(OptimisticLockingFailureException.class, () -> transactions.write(() -> repository.save(stale)));
        assertEquals(BookingStatus.CANCELLED, queries.getBooking(7, false, booking.id()).status());
    }

    @Test
    void myBookingsAreNewestFirstAndPaged() {
        int userId = 500_000 + (int) (showtimeId % 1_000_000);
        var first = holdSeats.hold(userId, showtimeId, List.of(101));
        var second = holdSeats.hold(userId, showtimeId, List.of(102));

        var page = queries.myBookings(userId, 0, 1);
        assertEquals(List.of(second.id()), page.content().stream().map(b -> b.id()).toList());
        assertEquals(2, page.totalElements());
        assertEquals(2, page.totalPages());
        assertTrue(page.first());
        assertFalse(page.last());
        assertEquals(List.of(first.id()), queries.myBookings(userId, 1, 1).content().stream().map(b -> b.id()).toList());
    }

    @Test
    void comboMenuOnlyShowsWhatIsOnSale() {
        var combo = comboService.create(new ComboCommand("Bắp nước " + showtimeId, "1 bắp", new BigDecimal("85000")));
        assertEquals(ComboStatus.ACTIVE, combo.status());
        assertTrue(comboService.listOnSale().contains(combo));

        var updated = comboService.update(combo.id(), new ComboCommand("Bắp lớn " + showtimeId, null, new BigDecimal("95000")));
        assertEquals(new BigDecimal("95000.00"), updated.price());

        comboService.deactivate(combo.id());
        assertTrue(comboService.listOnSale().stream().noneMatch(c -> c.id().equals(combo.id())));
        assertTrue(comboService.listAll().stream().anyMatch(c -> c.id().equals(combo.id())));
        assertEquals(ComboStatus.ACTIVE, comboService.activate(combo.id()).status());
    }

    @Test
    void combosArePaidTogetherWithSeats() {
        var combo = comboOnSale("85000");
        var booking = holdSeats.hold(7, showtimeId, List.of(101));
        var seatIds = seatRowIds(booking.id());

        var updated = replaceCombos.replace(7, booking.id(), List.of(new ComboLine(combo, 2)));
        assertEquals(new BigDecimal("260000.00"), updated.totalAmount());
        assertEquals(0, new BigDecimal("260000").compareTo(storedTotal(booking.id())));
        assertEquals(seatIds, seatRowIds(booking.id()));

        var payment = initiatePayment.initiate(7, booking.id());
        assertEquals(new BigDecimal("260000.00"), payment.amount());
        verify(gateway).createLink(argThat(request -> request.amount() == 260000L));

        when(gateway.verifyWebhook(any())).thenReturn(
            new VerifiedPayment(payment.orderCode(), 260000, true, "FT6", LocalDateTime.now(clock)));
        webhook.handle(Map.of());

        var confirmed = queries.getBooking(7, false, booking.id());
        assertEquals(BookingStatus.CONFIRMED, confirmed.status());
        assertEquals(1, confirmed.combos().size());
        assertEquals(2, confirmed.combos().getFirst().quantity());
        assertEquals(new BigDecimal("85000.00"), confirmed.combos().getFirst().price());
        assertEquals(seatIds, seatRowIds(booking.id()));
        assertThrows(InvalidStateTransitionException.class, () -> replaceCombos.replace(7, booking.id(), List.of()));
    }

    @Test
    void replacingCombosKeepsMatchingRowsAndDeletesTheRest() {
        var kept = comboOnSale("85000");
        var dropped = comboOnSale("40000");
        var booking = holdSeats.hold(7, showtimeId, List.of(101));
        replaceCombos.replace(7, booking.id(), List.of(new ComboLine(kept, 1), new ComboLine(dropped, 1)));
        var keptRow = comboRowId(booking.id(), kept);

        replaceCombos.replace(7, booking.id(), List.of(new ComboLine(kept, 3)));
        assertEquals(keptRow, comboRowId(booking.id(), kept));
        assertEquals(1, comboRowCount(booking.id()));
        assertEquals(0, new BigDecimal("345000").compareTo(storedTotal(booking.id())));

        replaceCombos.replace(7, booking.id(), List.of());
        assertEquals(0, comboRowCount(booking.id()));
        assertEquals(0, new BigDecimal("90000").compareTo(storedTotal(booking.id())));
    }

    @Test
    void comboPriceIsFixedAtPurchase() {
        var combo = comboOnSale("85000");
        var booking = holdSeats.hold(7, showtimeId, List.of(101));
        replaceCombos.replace(7, booking.id(), List.of(new ComboLine(combo, 1)));

        comboService.update(combo, new ComboCommand("Combo đổi tên " + combo, null, new BigDecimal("99000")));

        var stored = queries.getBooking(7, false, booking.id());
        assertEquals(new BigDecimal("85000.00"), stored.combos().getFirst().price());
        assertEquals(new BigDecimal("175000.00"), stored.totalAmount());
        assertEquals("Combo đổi tên " + combo, stored.combos().getFirst().name());
    }

    @Test
    void comboNotOnSaleCannotBeAdded() {
        var combo = comboOnSale("85000");
        comboService.deactivate(combo);
        var booking = holdSeats.hold(7, showtimeId, List.of(101));
        assertThrows(ComboUnavailableException.class,
            () -> replaceCombos.replace(7, booking.id(), List.of(new ComboLine(combo, 1))));
        assertEquals(0, comboRowCount(booking.id()));
    }

    @Test
    void combosCannotChangeOnceAPaymentLinkExists() {
        var combo = comboOnSale("85000");
        var booking = holdSeats.hold(7, showtimeId, List.of(101));
        initiatePayment.initiate(7, booking.id());
        assertThrows(InvalidStateTransitionException.class,
            () -> replaceCombos.replace(7, booking.id(), List.of(new ComboLine(combo, 1))));
        assertEquals(0, new BigDecimal("90000").compareTo(storedTotal(booking.id())));
    }

    @Test
    void afterAFailedLinkCombosCanChangeAndTheNextAttemptCarriesTheNewAmount() {
        var combo = comboOnSale("85000");
        var booking = holdSeats.hold(7, showtimeId, List.of(101));
        doThrow(new PaymentGatewayException("down", null)).when(gateway).createLink(any());
        assertThrows(PaymentGatewayException.class, () -> initiatePayment.initiate(7, booking.id()));

        doAnswer(invocation -> {
            PaymentGateway.LinkRequest request = invocation.getArgument(0);
            return new CheckoutLink("link-" + request.orderCode(), "https://pay.payos.vn/web/" + request.orderCode());
        }).when(gateway).createLink(any());
        replaceCombos.replace(7, booking.id(), List.of(new ComboLine(combo, 1)));

        var payment = initiatePayment.initiate(7, booking.id());
        assertEquals(new BigDecimal("175000.00"), payment.amount());
    }

    @Test
    void changingCombosWhilePayingNeverOpensALinkWithTheWrongAmount() throws Exception {
        var combo = comboOnSale("85000");
        for (int round = 0; round < 8; round++) {
            var booking = holdSeats.hold(7, showtimeId, List.of(101));
            var barrier = new CyclicBarrier(2);
            Callable<Object> change = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                try {
                    return replaceCombos.replace(7, booking.id(), List.of(new ComboLine(combo, 1)));
                } catch (InvalidStateTransitionException exception) {
                    return null;
                }
            };
            Callable<Object> pay = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return initiatePayment.initiate(7, booking.id());
            };
            try (var executor = Executors.newFixedThreadPool(2)) {
                for (var result : executor.invokeAll(List.of(change, pay))) result.get();
            }
            var stored = queries.getBooking(7, false, booking.id());
            assertEquals(stored.totalAmount(), stored.payment().amount());
            cancelBooking.cancel(7, booking.id());
        }
    }

    @Test
    void searchFiltersByEachParameterAndTheirCombination() {
        int user = uniqueUser();
        var held = holdSeats.hold(user, showtimeId, List.of(101));
        var cancelled = holdSeats.hold(user + 1, showtimeId, List.of(102));
        cancelBooking.cancel(user + 1, cancelled.id());

        assertEquals(List.of(cancelled.id(), held.id()), ids(onShowtime(null, null, null, null, null)));
        assertEquals(List.of(held.id()), ids(onShowtime(BookingStatus.PENDING_PAYMENT, null, null, null, null)));
        assertEquals(List.of(cancelled.id()), ids(onShowtime(null, user + 1, null, null, null)));
        assertEquals(List.of(held.id()), ids(onShowtime(null, null, held.bookingCode(), null, null)));
        assertEquals(List.of(), ids(onShowtime(BookingStatus.CANCELLED, user, null, null, null)));
        assertEquals(List.of(held.id()), ids(onShowtime(BookingStatus.PENDING_PAYMENT, user, held.bookingCode(), null, null)));

        var byUser = queries.search(new BookingSearch(null, null, user, null, null, null), 0, 10);
        assertEquals(List.of(held.id()), ids(byUser));
        assertTrue(ids(queries.search(new BookingSearch(null, showtimeId + 1_000_000_000L, null, null, null, null), 0, 10)).isEmpty());
    }

    @Test
    void searchMatchesTheWholeCodeIgnoringCaseAndSpaces() {
        var booking = holdSeats.hold(uniqueUser(), showtimeId, List.of(101));
        var typed = "  " + booking.bookingCode().toLowerCase() + " ";
        assertEquals(List.of(booking.id()), ids(onShowtime(null, null, typed, null, null)));
        assertEquals(List.of(), ids(onShowtime(null, null, booking.bookingCode().substring(0, 8), null, null)));
        assertEquals(List.of(booking.id()), ids(onShowtime(null, null, "   ", null, null)));
    }

    @Test
    void searchIncludesFromAndExcludesTo() {
        var booking = holdSeats.hold(uniqueUser(), showtimeId, List.of(101));
        var time = queries.getBooking(0, true, booking.id()).bookingTime();
        assertEquals(List.of(booking.id()), ids(onShowtime(null, null, null, time, null)));
        assertEquals(List.of(), ids(onShowtime(null, null, null, null, time)));
        assertEquals(List.of(booking.id()), ids(onShowtime(null, null, null, time, time.plusNanos(1000))));
        assertEquals(List.of(), ids(onShowtime(null, null, null, time.plusNanos(1000), time.plusDays(1))));
    }

    @Test
    void searchRejectsAnEmptyTimeRangeAndAnInvalidPage() {
        var now = LocalDateTime.now(clock);
        assertThrows(IllegalArgumentException.class, () -> onShowtime(null, null, null, now, now));
        assertThrows(IllegalArgumentException.class, () -> onShowtime(null, null, null, now.plusDays(1), now));
        var any = new BookingSearch(null, null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> queries.search(any, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> queries.search(any, 0, 101));
        assertThrows(IllegalArgumentException.class, () -> queries.search(any, -1, 10));
    }

    @Test
    void searchIsNewestFirstAndPaged() {
        int user = uniqueUser();
        var first = holdSeats.hold(user, showtimeId, List.of(101));
        clock.advance(Duration.ofMinutes(1));
        var second = holdSeats.hold(user, showtimeId, List.of(102));
        var search = new BookingSearch(null, showtimeId, null, null, null, null);

        var page = queries.search(search, 0, 1);
        assertEquals(List.of(second.id()), ids(page));
        assertEquals(2, page.totalElements());
        assertEquals(2, page.totalPages());
        assertTrue(page.first());
        assertFalse(page.last());
        assertEquals(List.of(first.id()), ids(queries.search(search, 1, 1)));
    }

    @Test
    void listsShowTheLatestPaymentOfEachBooking() {
        int user = uniqueUser();
        var booking = holdSeats.hold(user, showtimeId, List.of(101));
        doThrow(new PaymentGatewayException("down", null)).when(gateway).createLink(any());
        assertThrows(PaymentGatewayException.class, () -> initiatePayment.initiate(user, booking.id()));
        doAnswer(invocation -> {
            PaymentGateway.LinkRequest request = invocation.getArgument(0);
            return new CheckoutLink("link-" + request.orderCode(), "https://pay.payos.vn/web/" + request.orderCode());
        }).when(gateway).createLink(any());
        var latest = initiatePayment.initiate(user, booking.id());
        var withoutPayment = holdSeats.hold(user, showtimeId, List.of(102));

        var found = onShowtime(null, null, null, null, null).content();
        assertEquals(withoutPayment.id(), found.getFirst().id());
        assertNull(found.getFirst().payment());
        assertEquals(latest.id(), found.getLast().payment().id());
        assertEquals(PaymentStatus.PENDING, found.getLast().payment().status());
        assertEquals(latest.id(), queries.myBookings(user, 0, 10).content().getLast().payment().id());
    }

    @Test
    void queryCountDoesNotGrowWithThePage() {
        int user = uniqueUser();
        var combo = comboOnSale("85000");
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        addClosedBookingsWithCombosAndPayments(user, combo, 2);
        statistics.clear();
        assertEquals(2, queries.search(new BookingSearch(null, null, user, null, null, null), 0, 100).content().size());
        long forTwo = statistics.getPrepareStatementCount();

        addClosedBookingsWithCombosAndPayments(user, combo, 6);
        statistics.clear();
        assertEquals(8, queries.search(new BookingSearch(null, null, user, null, null, null), 0, 100).content().size());
        assertEquals(forTwo, statistics.getPrepareStatementCount());
    }

    @Test
    void publishingEventsOutsideATransactionFailsLoudly() {
        assertThrows(IllegalStateException.class, () -> domainEvents.publish(List.of(new BookingCancelled(1))));
    }

    private void addClosedBookingsWithCombosAndPayments(int user, Integer combo, int count) {
        for (int i = 0; i < count; i++) {
            var booking = holdSeats.hold(user, showtimeId, List.of(101));
            replaceCombos.replace(user, booking.id(), List.of(new ComboLine(combo, 1)));
            initiatePayment.initiate(user, booking.id());
            cancelBooking.cancel(user, booking.id());
        }
    }

    private BookingPage<BookingView> onShowtime(BookingStatus status, Integer userId, String code, LocalDateTime from,
                                                LocalDateTime to) {
        return queries.search(new BookingSearch(status, showtimeId, userId, code, from, to), 0, 10);
    }

    private static List<Integer> ids(BookingPage<BookingView> page) {
        return page.content().stream().map(BookingView::id).toList();
    }

    private int uniqueUser() {
        return (int) (showtimeId % 1_000_000_000L) * 2 + 1;
    }

    private Integer comboOnSale(String price) {
        return comboService.create(new ComboCommand("Combo " + System.nanoTime(), null, new BigDecimal(price))).id();
    }

    private BigDecimal storedTotal(Integer bookingId) {
        return jdbc.queryForObject("select total_amount from bookings where id = ?", BigDecimal.class, bookingId);
    }

    private Integer comboRowId(Integer bookingId, Integer comboId) {
        return jdbc.queryForObject("select id from booking_combos where booking_id = ? and combo_id = ?", Integer.class,
            bookingId, comboId);
    }

    private int comboRowCount(Integer bookingId) {
        return jdbc.queryForObject("select count(*) from booking_combos where booking_id = ?", Integer.class, bookingId);
    }

    private List<Integer> seatRowIds(Integer bookingId) {
        return jdbc.queryForList("select id from booking_seats where booking_id = ? order by id", Integer.class, bookingId);
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
