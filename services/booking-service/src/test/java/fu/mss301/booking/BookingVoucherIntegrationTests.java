package fu.mss301.booking;

import fu.mss301.booking.BookingServiceApplicationTests.MutableClock;
import fu.mss301.booking.application.booking.ApplyVoucherUseCase;
import fu.mss301.booking.application.booking.BookingQueryService;
import fu.mss301.booking.application.booking.BookingView;
import fu.mss301.booking.application.booking.CancelBookingUseCase;
import fu.mss301.booking.application.booking.ComboLine;
import fu.mss301.booking.application.booking.ExpireOverdueBookingsUseCase;
import fu.mss301.booking.application.booking.HoldSeatsUseCase;
import fu.mss301.booking.application.booking.ReplaceBookingCombosUseCase;
import fu.mss301.booking.application.combo.ComboCommand;
import fu.mss301.booking.application.combo.ComboService;
import fu.mss301.booking.application.exception.VoucherNotFoundException;
import fu.mss301.booking.application.payment.HandlePayosWebhookUseCase;
import fu.mss301.booking.application.payment.InitiatePaymentUseCase;
import fu.mss301.booking.application.payment.PaymentSettlementService;
import fu.mss301.booking.application.payment.PaymentView;
import fu.mss301.booking.application.payment.ReconcilePaymentUseCase;
import fu.mss301.booking.application.exception.PaymentGatewayException;
import fu.mss301.booking.application.voucher.ValueVoucherIssuer;
import fu.mss301.booking.domain.voucher.VoucherAvailability;
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
import fu.mss301.booking.application.voucher.VoucherCommand;
import fu.mss301.booking.application.voucher.VoucherReservations;
import fu.mss301.booking.application.voucher.VoucherService;
import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;
import fu.mss301.booking.domain.voucher.DiscountType;
import fu.mss301.booking.domain.voucher.VoucherKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
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

/** Applying vouchers to bookings against a real MySQL database. Seat 101 costs 90,000 and seat 102 (VIP) 150,000. */
@SpringBootTest(properties = {
    "spring.datasource.url=${BOOKING_TEST_DB_URL}",
    "spring.datasource.username=${BOOKING_TEST_DB_USERNAME:booking}",
    "spring.datasource.password=${BOOKING_TEST_DB_PASSWORD:booking_test}",
    "eureka.client.enabled=false",
    "booking.scheduling.enabled=false",
    "booking.hold-ttl=PT5M"
})
@EnabledIfEnvironmentVariable(named = "BOOKING_TEST_DB_URL", matches = ".+")
class BookingVoucherIntegrationTests {
    private static final AtomicLong IDS = new AtomicLong(System.nanoTime() % 1_000_000_000L + 500_000_000L);

    @Autowired private HoldSeatsUseCase holdSeats;
    @Autowired private ApplyVoucherUseCase vouchers;
    @Autowired private VoucherService voucherService;
    @Autowired private VoucherReservations reservations;
    @Autowired private InitiatePaymentUseCase initiatePayment;
    @Autowired private HandlePayosWebhookUseCase webhook;
    @Autowired private CancelBookingUseCase cancelBooking;
    @Autowired private ExpireOverdueBookingsUseCase expireOverdue;
    @Autowired private ReplaceBookingCombosUseCase replaceCombos;
    @Autowired private ComboService comboService;
    @Autowired private BookingQueryService queries;
    @Autowired private PaymentSettlementService settlement;
    @Autowired private ReconcilePaymentUseCase reconcile;
    @Autowired private ValueVoucherIssuer valueVouchers;
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
        openShowtime();
        when(showtimes.getSeatPrices(showtimeId))
            .thenReturn(Map.of(2, new SeatTypePrice(77, 2, new BigDecimal("150000"))));
        when(movies.getMovie(8)).thenReturn(new MovieCatalog.MovieInfo("Dune", null));
        when(cinemas.cinemaName(2)).thenReturn("Rạp Hà Nội");
        when(cinemas.screenName(3)).thenReturn("Phòng chiếu 1");
        when(cinemas.seatTypeNames()).thenReturn(Map.of(1, "Ghế thường", 2, "Ghế VIP"));
        when(seatLayout.getSeats(3)).thenReturn(List.of(
            new PhysicalSeat(101, "A", 1, 1, true),
            new PhysicalSeat(102, "A", 2, 2, true),
            new PhysicalSeat(104, "A", 4, 1, true)));
        when(gateway.createLink(any())).thenAnswer(invocation -> {
            LinkRequest request = invocation.getArgument(0);
            return new CheckoutLink("link-" + request.orderCode(), "https://pay.payos.vn/web/" + request.orderCode());
        });
        when(gateway.getLinkState(anyLong())).thenReturn(new LinkState(LinkStatus.PENDING, 0, null, null));
    }

    @Test
    void aTicketExchangeLowersTheAmountSentToPayosAndIsUsedUpWhenPaid() {
        String code = voucher(VoucherKind.TICKET_EXCHANGE, null, null, null, 2);
        var booking = holdSeats.hold(user, showtimeId, List.of(101, 102));
        assertEquals(new BigDecimal("0.00"), booking.discountAmount());
        assertEquals(booking.totalAmount(), booking.subtotal());

        var applied = vouchers.apply(user, booking.id(), code.toLowerCase(), 102);
        assertEquals(new BigDecimal("240000.00"), applied.subtotal());
        assertEquals(new BigDecimal("150000.00"), applied.discountAmount());
        assertEquals(new BigDecimal("90000.00"), applied.totalAmount());
        assertEquals(List.of(102), applied.vouchers().stream().map(BookingView.VoucherLine::seatId).toList());
        assertEquals("RESERVED", claimStatus(code));
        assertEquals(booking.id(), claimBooking(code));
        assertEquals(new BigDecimal("150000.00"), jdbc.queryForObject(
            "select discount_amount from bookings where id = ?", BigDecimal.class, booking.id()));
        assertEquals(2, jdbc.queryForObject("select seat_type_id from booking_seats where booking_id = ? and seat_id = 102",
            Integer.class, booking.id()));

        var payment = initiatePayment.initiate(user, booking.id());
        var sent = ArgumentCaptor.forClass(LinkRequest.class);
        verify(gateway).createLink(sent.capture());
        assertEquals(90000L, sent.getValue().amount());
        assertEquals(new BigDecimal("90000.00"), payment.amount());

        when(gateway.verifyWebhook(any())).thenReturn(
            new VerifiedPayment(payment.orderCode(), 90000, true, "FT1", LocalDateTime.now(clock)));
        webhook.handle(Map.of());
        assertEquals(BookingStatus.CONFIRMED, queries.getBooking(user, false, booking.id()).status());
        assertEquals("USED", claimStatus(code));
        assertThrows(InvalidStateTransitionException.class,
            () -> vouchers.apply(user, holdSeats.hold(user, showtimeId, List.of(104)).id(), code, 104));
        assertEquals("USED", claimStatus(code));
    }

    @Test
    void aVoucherCannotBeUsedOnTwoBookingsAtOnce() throws Exception {
        String code = voucher(VoucherKind.TICKET_DISCOUNT, DiscountType.FIXED, "10000", null, null);
        var bookingIds = List.of(holdSeats.hold(user, showtimeId, List.of(101)).id(),
            holdSeats.hold(user, showtimeId, List.of(102)).id(), holdSeats.hold(user, showtimeId, List.of(104)).id());
        var barrier = new CyclicBarrier(bookingIds.size());
        List<Callable<Integer>> attempts = bookingIds.stream().<Callable<Integer>>map(id -> () -> {
            barrier.await(5, TimeUnit.SECONDS);
            try {
                vouchers.apply(user, id, code, null);
                return id;
            } catch (InvalidStateTransitionException exception) {
                return null;
            }
        }).toList();
        Integer winner = null;
        int wins = 0;
        try (var executor = Executors.newFixedThreadPool(bookingIds.size())) {
            for (var result : executor.invokeAll(attempts)) {
                if (result.get() != null) {
                    winner = result.get();
                    wins++;
                }
            }
        }
        assertEquals(1, wins);
        assertEquals(winner, claimBooking(code));
        assertEquals(1, jdbc.queryForObject("select count(*) from booking_vouchers where voucher_id = ?", Integer.class,
            voucherId(code)));
    }

    @Test
    void cancellingOrExpiringABookingGivesTheVoucherBack() {
        String code = voucher(VoucherKind.TICKET_DISCOUNT, DiscountType.PERCENT, "10", null, null);
        var cancelled = holdSeats.hold(user, showtimeId, List.of(101));
        vouchers.apply(user, cancelled.id(), code, null);
        cancelBooking.cancel(user, cancelled.id());
        assertEquals("AVAILABLE", claimStatus(code));
        assertNull(claimBooking(code));

        var expired = holdSeats.hold(user, showtimeId, List.of(102));
        assertEquals(new BigDecimal("135000.00"), vouchers.apply(user, expired.id(), code, null).totalAmount());
        clock.advance(Duration.ofMinutes(6));
        expireOverdue.expireOverdue(1000);
        assertEquals(BookingStatus.EXPIRED, queries.getBooking(user, false, expired.id()).status());
        assertEquals("AVAILABLE", claimStatus(code));
        // The finished booking still shows the voucher it had.
        assertEquals(1, queries.getBooking(user, false, expired.id()).vouchers().size());
    }

    @Test
    void removingAVoucherGivesItBackAndRestoresThePrice() {
        String code = voucher(VoucherKind.TICKET_EXCHANGE, null, null, null, null);
        var booking = holdSeats.hold(user, showtimeId, List.of(101));
        var applied = vouchers.apply(user, booking.id(), code, 101);
        assertEquals(new BigDecimal("0.00"), applied.totalAmount());
        Integer voucherId = applied.vouchers().getFirst().voucherId();

        assertThrows(VoucherNotFoundException.class, () -> vouchers.remove(user, booking.id(), voucherId + 100000));
        var removed = vouchers.remove(user, booking.id(), voucherId);
        assertEquals(new BigDecimal("90000.00"), removed.totalAmount());
        assertTrue(removed.vouchers().isEmpty());
        assertEquals("AVAILABLE", claimStatus(code));
        assertEquals(0, jdbc.queryForObject("select count(*) from booking_vouchers where booking_id = ?", Integer.class,
            booking.id()));
        assertEquals(new BigDecimal("0.00"), vouchers.apply(user, booking.id(), code, 101).totalAmount());
    }

    @Test
    void aRejectedVoucherIsGivenBackAndSomeoneElsesCodeLooksUnknown() {
        String code = voucher(VoucherKind.TICKET_EXCHANGE, null, null, null, 2);
        var booking = holdSeats.hold(user, showtimeId, List.of(101));
        assertThrows(DomainException.class, () -> vouchers.apply(user, booking.id(), code, null));
        assertEquals("AVAILABLE", claimStatus(code));
        assertThrows(DomainException.class, () -> vouchers.apply(user, booking.id(), code, 101), "seat type differs");
        assertEquals("AVAILABLE", claimStatus(code));

        var stranger = holdSeats.hold(user + 1, showtimeId, List.of(102));
        assertThrows(VoucherNotFoundException.class, () -> vouchers.apply(user + 1, stranger.id(), code, 102));
        assertThrows(VoucherNotFoundException.class, () -> vouchers.apply(user + 1, stranger.id(), "EMNOSUCHCODE1", 102));
        assertEquals("AVAILABLE", claimStatus(code));
        assertNull(claimBooking(code));
    }

    @Test
    void aVoucherLeavingLessThan2000IsRefusedAndSuchAnAmountIsNeverSentToPayos() {
        String tooBig = voucher(VoucherKind.TICKET_DISCOUNT, DiscountType.FIXED, "89000", null, null);
        var booking = holdSeats.hold(user, showtimeId, List.of(101));
        assertThrows(DomainException.class, () -> vouchers.apply(user, booking.id(), tooBig, null));
        assertEquals("AVAILABLE", claimStatus(tooBig));

        Integer combo = comboService.create(new ComboCommand("Combo " + System.nanoTime(), null, new BigDecimal("1500")))
            .id();
        replaceCombos.replace(user, booking.id(), List.of(new ComboLine(combo, 2)));
        String free = voucher(VoucherKind.TICKET_EXCHANGE, null, null, null, null);
        assertEquals(new BigDecimal("3000.00"), vouchers.apply(user, booking.id(), free, 101).totalAmount());
        assertEquals(new BigDecimal("1500.00"),
            replaceCombos.replace(user, booking.id(), List.of(new ComboLine(combo, 1))).totalAmount());
        assertThrows(DomainException.class, () -> initiatePayment.initiate(user, booking.id()));
        verify(gateway, never()).createLink(any());
        assertEquals(0, jdbc.queryForObject("select count(*) from payment_transactions where booking_id = ?",
            Integer.class, booking.id()));
    }

    @Test
    void vouchersCannotChangeOnceALinkExists() {
        String code = voucher(VoucherKind.TICKET_DISCOUNT, DiscountType.FIXED, "10000", null, null);
        var booking = holdSeats.hold(user, showtimeId, List.of(101));
        initiatePayment.initiate(user, booking.id());
        assertThrows(InvalidStateTransitionException.class, () -> vouchers.apply(user, booking.id(), code, null));
        assertEquals("AVAILABLE", claimStatus(code));
        assertNull(claimBooking(code));
    }

    @Test
    void aBookingVouchersPayInFullIsConfirmedWithoutPayos() {
        String code = voucher(VoucherKind.TICKET_EXCHANGE, null, null, null, null);
        var booking = holdSeats.hold(user, showtimeId, List.of(101));
        vouchers.apply(user, booking.id(), code, 101);

        var payment = initiatePayment.initiate(user, booking.id());
        assertEquals(PaymentView.NOT_REQUIRED, payment.status());
        assertEquals(new BigDecimal("0.00"), payment.amount());
        assertNull(payment.checkoutUrl());
        verify(gateway, never()).createLink(any());
        assertEquals(0, jdbc.queryForObject("select count(*) from payment_transactions where booking_id = ?",
            Integer.class, booking.id()));
        var confirmed = queries.getBooking(user, false, booking.id());
        assertEquals(BookingStatus.CONFIRMED, confirmed.status());
        assertNull(confirmed.payment());
        assertEquals("USED", claimStatus(code));
        assertThrows(InvalidStateTransitionException.class, () -> initiatePayment.initiate(user, booking.id()));
    }

    @Test
    void aVoucherWhoseLastDayPassedComesOffBeforeTheLinkIsCreated() {
        LocalDateTime lateEvening = LocalDate.now(clock).plusDays(1).atTime(23, 57);
        clock.advance(Duration.between(LocalDateTime.now(clock), lateEvening));
        openShowtime();
        String code = voucher(VoucherKind.TICKET_DISCOUNT, DiscountType.FIXED, "20000", null, null,
            lateEvening.toLocalDate());
        var booking = holdSeats.hold(user, showtimeId, List.of(101));
        assertEquals(new BigDecimal("70000.00"), vouchers.apply(user, booking.id(), code, null).totalAmount());

        clock.advance(Duration.ofMinutes(4));
        var payment = initiatePayment.initiate(user, booking.id());
        assertEquals(new BigDecimal("90000.00"), payment.amount());
        var sent = ArgumentCaptor.forClass(LinkRequest.class);
        verify(gateway).createLink(sent.capture());
        assertEquals(90000L, sent.getValue().amount());
        assertTrue(queries.getBooking(user, false, booking.id()).vouchers().isEmpty());
        assertEquals("AVAILABLE", claimStatus(code));
    }

    @Test
    void fewerCombosTakeOffAComboDiscountBelowItsMinimumAndGiveItBack() {
        Integer combo = comboService.create(new ComboCommand("Combo " + System.nanoTime(), null, new BigDecimal("85000")))
            .id();
        String code = voucher(VoucherKind.COMBO_DISCOUNT, DiscountType.PERCENT, "20", "150000", null);
        var booking = holdSeats.hold(user, showtimeId, List.of(101));
        replaceCombos.replace(user, booking.id(), List.of(new ComboLine(combo, 2)));
        assertEquals(new BigDecimal("226000.00"), vouchers.apply(user, booking.id(), code, null).totalAmount());

        var fewer = replaceCombos.replace(user, booking.id(), List.of(new ComboLine(combo, 1)));
        assertTrue(fewer.vouchers().isEmpty());
        assertEquals(new BigDecimal("175000.00"), fewer.totalAmount());
        assertEquals("AVAILABLE", claimStatus(code));
    }

    @Test
    void theJobSettlesVouchersWhoseEventWasLost() {
        String paidCode = voucher(VoucherKind.TICKET_DISCOUNT, DiscountType.FIXED, "10000", null, null);
        var paid = holdSeats.hold(user, showtimeId, List.of(101));
        vouchers.apply(user, paid.id(), paidCode, null);
        var payment = initiatePayment.initiate(user, paid.id());
        when(gateway.verifyWebhook(any())).thenReturn(
            new VerifiedPayment(payment.orderCode(), 80000, true, "FT9", LocalDateTime.now(clock)));
        webhook.handle(Map.of());

        String strandedCode = voucher(VoucherKind.TICKET_DISCOUNT, DiscountType.FIXED, "10000", null, null);
        var pending = holdSeats.hold(user, showtimeId, List.of(102));
        String heldCode = voucher(VoucherKind.TICKET_EXCHANGE, null, null, null, null);
        vouchers.apply(user, pending.id(), heldCode, 102);

        // As if the confirm event was lost, and as if the service stopped between holding a voucher and the booking.
        LocalDateTime old = LocalDateTime.now(clock).minusMinutes(10);
        jdbc.update("update user_vouchers set status = 'RESERVED', used_at = null, updated_at = ? where voucher_id = ?",
            old, voucherId(paidCode));
        jdbc.update("update user_vouchers set status = 'RESERVED', booking_id = ?, updated_at = ? where voucher_id = ?",
            pending.id(), old, voucherId(strandedCode));
        jdbc.update("update user_vouchers set updated_at = ? where voucher_id = ?", old, voucherId(heldCode));

        assertTrue(reservations.settleStale(1000) >= 3);
        assertEquals("USED", claimStatus(paidCode));
        assertEquals("AVAILABLE", claimStatus(strandedCode));
        assertEquals("RESERVED", claimStatus(heldCode), "a pending booking still holds its voucher");
    }

    @Test
    void aLatePaymentBecomesOneValueVoucherForTheCustomer() {
        var booking = holdSeats.hold(user, showtimeId, List.of(101, 102));
        var payment = initiatePayment.initiate(user, booking.id());
        clock.advance(Duration.ofMinutes(6));
        expireOverdue.expireOverdue(1000);
        payLate(payment.orderCode(), 240000);
        payLate(payment.orderCode(), 240000);
        settlement.settle(payment.id(), booking.id());

        var given = voucherService.mine(user).stream().filter(v -> v.kind() == VoucherKind.VALUE).toList();
        assertEquals(1, given.size());
        var voucher = given.getFirst();
        assertEquals(new BigDecimal("240000.00"), voucher.discountValue());
        assertEquals(LocalDate.now(clock), voucher.startDate());
        assertEquals(LocalDate.now(clock).plusDays(89), voucher.endDate());
        assertEquals(VoucherAvailability.AVAILABLE, voucher.status());
        assertEquals(payment.id(), voucherService.get(voucher.id()).sourcePaymentId());
        assertEquals("REFUNDED", queries.getBooking(user, false, booking.id()).payment().status());
        assertEquals(List.of("VOUCHER:COMPLETED:" + voucher.id()), jdbc.queryForList(
            "select concat(method, ':', status, ':', voucher_id) from refunds where payment_id = ?", String.class,
            payment.id()));
    }

    @Test
    void aSecondPaymentForAPaidBookingBecomesAVoucher() {
        var booking = holdSeats.hold(user, showtimeId, List.of(101));
        doThrow(new PaymentGatewayException("down", null)).when(gateway).createLink(any());
        assertThrows(PaymentGatewayException.class, () -> initiatePayment.initiate(user, booking.id()));
        doAnswer(invocation -> {
            LinkRequest request = invocation.getArgument(0);
            return new CheckoutLink("link-" + request.orderCode(), "https://pay.payos.vn/web/" + request.orderCode());
        }).when(gateway).createLink(any());
        var second = initiatePayment.initiate(user, booking.id());

        payLate(booking.id() * 100L + 1, 90000);
        assertEquals(BookingStatus.CONFIRMED, queries.getBooking(user, false, booking.id()).status());
        payLate(second.orderCode(), 90000);

        assertEquals(1, jdbc.queryForObject("select count(*) from vouchers where source_payment_id = ?", Integer.class,
            second.id()));
        assertEquals("REFUNDED", jdbc.queryForObject("select status from payment_transactions where id = ?",
            String.class, second.id()));
        assertEquals("SUCCESS", jdbc.queryForObject("select status from payment_transactions where order_code = ?",
            String.class, booking.id() * 100L + 1));
    }

    @Test
    void theJobFinishesAGiveBackThatStoppedAfterTheVoucherWasIssued() {
        var booking = holdSeats.hold(user, showtimeId, List.of(101));
        var payment = initiatePayment.initiate(user, booking.id());
        cancelBooking.cancel(user, booking.id());
        // As if the service stopped right after issuing the voucher: the payment is paid but not yet given back.
        jdbc.update("update payment_transactions set status = 'SUCCESS', paid_at = now(6) where id = ?", payment.id());
        var issued = valueVouchers.forPayment(payment.id(), user, new BigDecimal("90000"));

        reconcile.settleUnsettled(1000);
        assertEquals("REFUNDED", jdbc.queryForObject("select status from payment_transactions where id = ?",
            String.class, payment.id()));
        assertEquals(issued.id(), jdbc.queryForObject("select voucher_id from refunds where payment_id = ?",
            Integer.class, payment.id()));
        assertEquals(1, jdbc.queryForObject("select count(*) from vouchers where source_payment_id = ?", Integer.class,
            payment.id()));
    }

    @Test
    void whatAValueVoucherDoesNotUseComesBackAsANewVoucher() {
        var value = valueVouchers.forPayment(latePayment(), user, new BigDecimal("240000"));
        var booking = holdSeats.hold(user, showtimeId, List.of(104));
        assertEquals(new BigDecimal("0.00"), vouchers.apply(user, booking.id(), value.code().value(), null).totalAmount());
        assertEquals(PaymentView.NOT_REQUIRED, initiatePayment.initiate(user, booking.id()).status());

        assertEquals("USED", claimStatus(value.code().value()));
        var rest = voucherService.mine(user).stream()
            .filter(v -> v.kind() == VoucherKind.VALUE && v.status() == VoucherAvailability.AVAILABLE).toList();
        assertEquals(1, rest.size());
        assertEquals(new BigDecimal("150000.00"), rest.getFirst().discountValue());
        assertEquals(booking.id(), voucherService.get(rest.getFirst().id()).sourceBookingId());

        reservations.settle(value.id(), booking.id());
        assertEquals(1, jdbc.queryForObject("select count(*) from vouchers where source_booking_id = ?", Integer.class,
            booking.id()));
    }

    @Test
    void aValueVoucherUsedInFullLeavesNothingBehind() {
        var value = valueVouchers.forPayment(latePayment(), user, new BigDecimal("50000"));
        var booking = holdSeats.hold(user, showtimeId, List.of(104));
        assertEquals(new BigDecimal("40000.00"), vouchers.apply(user, booking.id(), value.code().value(), null).totalAmount());
        var payment = initiatePayment.initiate(user, booking.id());
        payLate(payment.orderCode(), 40000);
        assertEquals("USED", claimStatus(value.code().value()));
        assertEquals(0, jdbc.queryForObject("select count(*) from vouchers where source_booking_id = ?", Integer.class,
            booking.id()));
    }

    /** A paid payment on a cancelled booking of the test user, to be the source of a value voucher. */
    private Integer latePayment() {
        var booking = holdSeats.hold(user, showtimeId, List.of(102));
        var payment = initiatePayment.initiate(user, booking.id());
        cancelBooking.cancel(user, booking.id());
        jdbc.update("update payment_transactions set status = 'SUCCESS', paid_at = now(6) where id = ?", payment.id());
        return payment.id();
    }

    private void payLate(long orderCode, long amount) {
        when(gateway.verifyWebhook(any())).thenReturn(
            new VerifiedPayment(orderCode, amount, true, "FT" + orderCode, LocalDateTime.now(clock)));
        webhook.handle(Map.of());
    }

    private void openShowtime() {
        LocalDateTime start = LocalDateTime.now(clock).plusDays(1);
        when(showtimes.getShowtime(showtimeId))
            .thenReturn(new ShowtimeSnapshot(showtimeId, 8, 2, 3, start, start.plusHours(2), new BigDecimal("90000"), true));
    }

    private String voucher(VoucherKind kind, DiscountType type, String value, String min, Integer seatTypeId) {
        return voucher(kind, type, value, min, seatTypeId, LocalDate.now(clock).plusDays(30));
    }

    /** Creates a voucher owned by the test user and returns its code. */
    private String voucher(VoucherKind kind, DiscountType type, String value, String min, Integer seatTypeId,
                           LocalDate endDate) {
        var created = voucherService.create(new VoucherCommand(null, kind, type, value == null ? null : new BigDecimal(value),
            LocalDate.now(clock).minusDays(1), endDate, min == null ? null : new BigDecimal(min), seatTypeId, null));
        voucherService.assign(created.id(), user);
        return created.code();
    }

    private Integer voucherId(String code) {
        return jdbc.queryForObject("select id from vouchers where code = ?", Integer.class, code);
    }

    private String claimStatus(String code) {
        return jdbc.queryForObject("select c.status from user_vouchers c join vouchers v on v.id = c.voucher_id "
            + "where v.code = ?", String.class, code);
    }

    private Integer claimBooking(String code) {
        return jdbc.queryForObject("select c.booking_id from user_vouchers c join vouchers v on v.id = c.voucher_id "
            + "where v.code = ?", Integer.class, code);
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
