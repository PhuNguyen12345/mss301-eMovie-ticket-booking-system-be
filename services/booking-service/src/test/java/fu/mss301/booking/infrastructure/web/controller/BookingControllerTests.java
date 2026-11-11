package fu.mss301.booking.infrastructure.web.controller;

import fu.mss301.booking.application.exception.BookingNotFoundException;
import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.shared.ErrorCode;
import fu.mss301.booking.application.booking.BookingPage;
import fu.mss301.booking.application.payment.PaymentHistoryQuery;
import fu.mss301.booking.application.payment.PaymentHistoryView;
import fu.mss301.booking.application.payment.PaymentSearch;
import fu.mss301.booking.application.port.PaymentQueries;
import fu.mss301.booking.domain.payment.PaymentStatus;
import fu.mss301.booking.domain.payment.RefundMethod;
import fu.mss301.booking.domain.payment.RefundStatus;
import fu.mss301.booking.application.port.BookingStatistics;
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.application.statistics.StatisticsFigures;
import fu.mss301.booking.application.statistics.StatisticsGroup;
import fu.mss301.booking.application.statistics.StatisticsQuery;
import fu.mss301.booking.application.statistics.StatisticsRow;
import java.util.Map;
import java.util.function.Supplier;
import fu.mss301.booking.application.booking.*;
import fu.mss301.booking.application.exception.ComboUnavailableException;
import fu.mss301.booking.application.exception.InvalidWebhookException;
import fu.mss301.booking.application.exception.SeatUnavailableException;
import fu.mss301.booking.application.exception.UpstreamServiceException;
import fu.mss301.booking.application.exception.TicketAlreadyUsedException;
import fu.mss301.booking.application.exception.VoucherNotFoundException;
import fu.mss301.booking.application.payment.PaymentView;
import fu.mss301.booking.domain.voucher.VoucherKind;
import fu.mss301.booking.application.payment.HandlePayosWebhookUseCase;
import fu.mss301.booking.application.payment.InitiatePaymentUseCase;
import fu.mss301.booking.application.payment.ReconcilePaymentUseCase;
import fu.mss301.booking.application.payment.RefundAdminUseCase;
import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;
import fu.mss301.booking.infrastructure.web.exception.BookingExceptionHandler;
import fu.mss301.booking.infrastructure.web.security.CurrentUserArgumentResolver;
import fu.mss301.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BookingControllerTests {
    private HoldSeatsUseCase holdSeats;
    private BookingQueryService queries;
    private InitiatePaymentUseCase initiatePayment;
    private ReplaceBookingCombosUseCase replaceCombos;
    private HandlePayosWebhookUseCase webhook;
    private RefundAdminUseCase refunds;
    private ApplyVoucherUseCase vouchers;
    private CheckInUseCase checkIn;
    private TicketQrUseCase ticketQr;
    private ShowtimeCancellationUseCase showtimeCancellation;
    private BookingStatistics statistics;
    private PaymentQueries paymentQueries;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        holdSeats = mock(HoldSeatsUseCase.class);
        queries = mock(BookingQueryService.class);
        initiatePayment = mock(InitiatePaymentUseCase.class);
        replaceCombos = mock(ReplaceBookingCombosUseCase.class);
        webhook = mock(HandlePayosWebhookUseCase.class);
        refunds = mock(RefundAdminUseCase.class);
        vouchers = mock(ApplyVoucherUseCase.class);
        checkIn = mock(CheckInUseCase.class);
        ticketQr = mock(TicketQrUseCase.class);
        showtimeCancellation = mock(ShowtimeCancellationUseCase.class);
        statistics = mock(BookingStatistics.class);
        paymentQueries = mock(PaymentQueries.class);
        when(paymentQueries.search(any(), anyInt(), anyInt()))
            .thenAnswer(call -> new BookingPage<>(List.of(), call.getArgument(1), call.getArgument(2), 0, 0, true, true));
        Transactions inline = new Transactions() {
            public <T> T read(Supplier<T> work) { return work.get(); }
            public <T> T write(Supplier<T> work) { return work.get(); }
        };
        mvc = MockMvcBuilders.standaloneSetup(
                new BookingController(mock(SeatMapQuery.class), holdSeats, queries,
                    mock(CancelBookingUseCase.class), initiatePayment, replaceCombos, vouchers, checkIn, ticketQr,
                    showtimeCancellation, new StatisticsQuery(statistics, inline)),
                new PaymentController(webhook, mock(ReconcilePaymentUseCase.class), refunds,
                    new PaymentHistoryQuery(paymentQueries, inline)))
            .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
            .setControllerAdvice(new BookingExceptionHandler(), new GlobalExceptionHandler())
            .build();
    }

    @Test
    void paymentHistoryPassesEveryFilterAndTurnsDaysIntoARange() throws Exception {
        var refund = new PaymentHistoryView.RefundLine(9, new BigDecimal("170000"), RefundMethod.VOUCHER, 88,
            RefundStatus.COMPLETED, "Showtime cancelled", LocalDateTime.of(2026, 11, 8, 10, 0),
            LocalDateTime.of(2026, 11, 8, 10, 0));
        var row = new PaymentHistoryView(51, 120, "BK261108100000ABCDEF", 7, 1762000L, "PAYOS",
            new BigDecimal("386000"), PaymentStatus.REFUNDED, "FT25", LocalDateTime.of(2026, 11, 8, 9, 0),
            LocalDateTime.of(2026, 11, 8, 8, 59), List.of(refund));
        var search = new PaymentSearch(PaymentStatus.REFUNDED, 120, 1762000L, LocalDateTime.of(2026, 11, 1, 0, 0),
            LocalDateTime.of(2026, 12, 1, 0, 0));
        when(paymentQueries.search(search, 2, 5)).thenReturn(new BookingPage<>(List.of(row), 2, 5, 11, 3, false, true));
        mvc.perform(get("/api/payments").param("status", "REFUNDED").param("bookingId", "120")
                .param("orderCode", "1762000").param("from", "2026-11-01").param("to", "2026-11-30")
                .param("page", "2").param("size", "5").header("X-User-Id", "1").header("X-User-Roles", "ADMIN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.data[0].bookingCode").value("BK261108100000ABCDEF"))
            .andExpect(jsonPath("$.data.data[0].status").value("REFUNDED"))
            .andExpect(jsonPath("$.data.data[0].refunds[0].voucherId").value(88))
            .andExpect(jsonPath("$.data.data[0].checkoutUrl").doesNotExist())
            .andExpect(jsonPath("$.data.totalElements").value(11));
        mvc.perform(get("/api/payments").header("X-User-Id", "1").header("X-User-Roles", "ADMIN"))
            .andExpect(status().isOk());
        verify(paymentQueries).search(new PaymentSearch(null, null, null, null, null), 0, 20);
    }

    @Test
    void onlyAdminsSeePaymentHistory() throws Exception {
        mvc.perform(get("/api/payments").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(paymentQueries);
    }

    @Test
    void invalidPaymentHistoryParametersAre400() throws Exception {
        for (var params : List.of(
                Map.of("from", "2026-11-30", "to", "2026-11-01"),
                Map.of("size", "0"),
                Map.of("size", "101"),
                Map.of("page", "-1"),
                Map.of("status", "PAID"),
                Map.of("from", "01-11-2026"))) {
            var request = get("/api/payments").header("X-User-Id", "1").header("X-User-Roles", "ADMIN");
            params.forEach((name, value) -> request.param(name, value));
            mvc.perform(request).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/payments").param("size", "100").param("from", "2026-11-01").param("to", "2026-11-01")
                .header("X-User-Id", "1").header("X-User-Roles", "ADMIN"))
            .andExpect(status().isOk());
        verify(paymentQueries).search(new PaymentSearch(null, null, null, LocalDateTime.of(2026, 11, 1, 0, 0),
            LocalDateTime.of(2026, 11, 2, 0, 0)), 0, 100);
        verifyNoMoreInteractions(paymentQueries);
    }

    @Test
    void statisticsUseShowtimeDatesAndAcceptLowerCaseGroups() throws Exception {
        var figures = new StatisticsFigures(2, 3, new BigDecimal("330000"), new BigDecimal("170000"),
            new BigDecimal("114000"), new BigDecimal("386000"), new BigDecimal("90000"));
        when(statistics.summarize(LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 12, 1, 0, 0),
            StatisticsGroup.MOVIE)).thenReturn(List.of(new StatisticsRow("25", "Mai", figures)));
        mvc.perform(get("/api/bookings/statistics").param("from", "2026-11-01").param("to", "2026-11-30")
                .param("groupBy", "movie").header("X-User-Id", "1").header("X-User-Roles", "ADMIN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.groupBy").value("MOVIE"))
            .andExpect(jsonPath("$.data.total.revenue").value(386000))
            .andExpect(jsonPath("$.data.rows[0].key").value("25"))
            .andExpect(jsonPath("$.data.rows[0].label").value("Mai"))
            .andExpect(jsonPath("$.data.rows[0].figures.tickets").value(3));
        mvc.perform(get("/api/bookings/statistics").param("from", "2026-11-01").param("to", "2026-11-01")
                .header("X-User-Id", "1").header("X-User-Roles", "ADMIN"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.groupBy").value("DAY"));
    }

    @Test
    void onlyAdminsSeeStatistics() throws Exception {
        mvc.perform(get("/api/bookings/statistics").param("from", "2026-11-01").param("to", "2026-11-30")
                .header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(statistics);
    }

    @Test
    void invalidStatisticsRangesAre400() throws Exception {
        for (var params : List.of(
                Map.of("from", "2026-11-30", "to", "2026-11-01"),
                Map.of("from", "2026-01-01", "to", "2027-01-02"),
                Map.of("from", "2026-11-01", "to", "2026-11-30", "groupBy", "week"),
                Map.of("from", "2026-11-01"),
                Map.of("from", "01-11-2026", "to", "2026-11-30"))) {
            var request = get("/api/bookings/statistics").header("X-User-Id", "1").header("X-User-Roles", "ADMIN");
            params.forEach((name, value) -> request.param(name, value));
            mvc.perform(request).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/bookings/statistics").param("from", "2026-01-01").param("to", "2026-12-31")
                .header("X-User-Id", "1").header("X-User-Roles", "ADMIN"))
            .andExpect(status().isOk());
        verify(statistics).summarize(LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2027, 1, 1, 0, 0),
            StatisticsGroup.DAY);
    }

    @Test
    void holdReturns201WithLocation() throws Exception {
        when(holdSeats.hold(7, 3L, List.of(101, 102))).thenReturn(new BookingView(11, "BK260101000000ABCDEF", 7, 3L,
            BookingStatus.PENDING_PAYMENT, new BigDecimal("180000.00"), BigDecimal.ZERO, new BigDecimal("180000.00"),
            LocalDateTime.now(), LocalDateTime.now(), null, List.of(), List.of(), List.of(), 0, null, null));
        mvc.perform(post("/api/bookings").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"showtimeId\":3,\"seatIds\":[101,102]}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "http://localhost/api/bookings/11"))
            .andExpect(jsonPath("$.code").doesNotExist())
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"));
    }

    @Test
    void adminSearchPassesEveryFilterToTheService() throws Exception {
        var search = new BookingSearch(BookingStatus.CONFIRMED, 3L, 7, "BK1", LocalDateTime.of(2027, 1, 20, 0, 0),
            LocalDateTime.of(2027, 1, 21, 0, 0));
        when(queries.search(search, 1, 5)).thenReturn(new BookingPage<>(List.of(), 1, 5, 6, 2, false, true));
        mvc.perform(get("/api/bookings").header("X-User-Id", "1").header("X-User-Roles", "ADMIN")
                .param("status", "CONFIRMED").param("showtimeId", "3").param("userId", "7").param("code", "BK1")
                .param("from", "2027-01-20T00:00:00").param("to", "2027-01-21T00:00:00")
                .param("page", "1").param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(6))
            .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    void adminSearchWithoutFiltersUsesTheDefaultPage() throws Exception {
        when(queries.search(new BookingSearch(null, null, null, null, null, null), 0, 10))
            .thenReturn(new BookingPage<>(List.of(), 0, 10, 0, 0, true, true));
        mvc.perform(get("/api/bookings").header("X-User-Id", "1").header("X-User-Roles", "ADMIN"))
            .andExpect(status().isOk());
    }

    @Test
    void onlyAdminsCanSearch() throws Exception {
        mvc.perform(get("/api/bookings").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
        verifyNoInteractions(queries);
    }

    @Test
    void malformedSearchParametersAre400() throws Exception {
        mvc.perform(get("/api/bookings").param("status", "NOPE").header("X-User-Id", "1").header("X-User-Roles", "ADMIN"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/bookings").param("from", "20-01-2027").header("X-User-Id", "1").header("X-User-Roles", "ADMIN"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void replacingCombosPassesTheLinesAndReturnsTheBooking() throws Exception {
        when(replaceCombos.replace(7, 11, List.of(new ComboLine(5, 2)))).thenReturn(new BookingView(11,
            "BK260101000000ABCDEF", 7, 3L, BookingStatus.PENDING_PAYMENT, new BigDecimal("260000.00"), BigDecimal.ZERO,
            new BigDecimal("260000.00"), LocalDateTime.now(), LocalDateTime.now(), null, List.of(),
            List.of(new BookingView.ComboItem(5, "Bắp nước", 2, new BigDecimal("85000.00"))), List.of(), 0, null, null));
        mvc.perform(put("/api/bookings/11/combos").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"combos\":[{\"comboId\":5,\"quantity\":2}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalAmount").value(260000.00))
            .andExpect(jsonPath("$.data.combos[0].name").value("Bắp nước"))
            .andExpect(jsonPath("$.data.combos[0].quantity").value(2));
    }

    @Test
    void applyingAVoucherPassesCodeAndSeat() throws Exception {
        var line = new BookingView.VoucherLine(9, "EMFREE", VoucherKind.TICKET_EXCHANGE, 101, null,
            new BigDecimal("90000.00"), LocalDate.of(2027, 2, 1));
        when(vouchers.apply(7, 11, "emfree", 101)).thenReturn(new BookingView(11, "BK260101000000ABCDEF", 7, 3L,
            BookingStatus.PENDING_PAYMENT, new BigDecimal("180000.00"), new BigDecimal("90000.00"),
            new BigDecimal("90000.00"), LocalDateTime.now(), LocalDateTime.now(), null, List.of(), List.of(), List.of(line),
            0, null, null));
        mvc.perform(post("/api/bookings/11/vouchers").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"emfree\",\"seatId\":101}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.subtotal").value(180000.00))
            .andExpect(jsonPath("$.data.discountAmount").value(90000.00))
            .andExpect(jsonPath("$.data.vouchers[0].kind").value("TICKET_EXCHANGE"))
            .andExpect(jsonPath("$.data.vouchers[0].seatId").value(101));
    }

    @Test
    void invalidVoucherBodiesAre400AndAnUnknownCodeIs404() throws Exception {
        for (var body : List.of("{}", "{\"code\":\" \"}", "{\"code\":\"EMX\",\"seatId\":0}")) {
            mvc.perform(post("/api/bookings/11/vouchers").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(vouchers);
        when(vouchers.apply(7, 11, "NOPE", null)).thenThrow(new VoucherNotFoundException());
        mvc.perform(post("/api/bookings/11/vouchers").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"NOPE\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("VOUCHER_NOT_FOUND"));
    }

    @Test
    void removingAVoucherUsesDelete() throws Exception {
        when(vouchers.remove(7, 11, 9)).thenThrow(new InvalidStateTransitionException("link open"));
        mvc.perform(delete("/api/bookings/11/vouchers/9").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"))
            .andExpect(jsonPath("$.message").value("link open"));
        verify(vouchers).remove(7, 11, 9);
    }

    @Test
    void aFreeBookingSaysNoPaymentIsNeeded() throws Exception {
        when(initiatePayment.initiate(7, 11)).thenReturn(PaymentView.notRequired(11));
        mvc.perform(post("/api/bookings/11/payments").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Booking confirmed, vouchers cover the whole amount"))
            .andExpect(jsonPath("$.data.status").value("NOT_REQUIRED"))
            .andExpect(jsonPath("$.data.amount").value(0))
            .andExpect(jsonPath("$.data.checkoutUrl").doesNotExist());
    }

    @Test
    void onlyAdminsCheckIn() throws Exception {
        mvc.perform(post("/api/bookings/check-in").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"bookingCode\":\"BK1\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(checkIn);
    }

    @Test
    void checkInPassesTheStaffCodeAndQuantity() throws Exception {
        when(checkIn.checkIn(1, "bk1", 2)).thenReturn(checkInView(2, 1));
        mvc.perform(post("/api/bookings/check-in").header("X-User-Id", "1").header("X-User-Roles", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content("{\"bookingCode\":\"bk1\",\"quantity\":2}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.admittedCount").value(2))
            .andExpect(jsonPath("$.data.remaining").value(1));
        for (var body : List.of("{}", "{\"bookingCode\":\" \"}", "{\"bookingCode\":\"BK1\",\"quantity\":0}")) {
            mvc.perform(post("/api/bookings/check-in").header("X-User-Id", "1").header("X-User-Roles", "ADMIN")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    void checkInOutsideTheWindowSaysWhenItOpensAndCloses() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 11, 11, 13, 25);
        when(checkIn.checkIn(1, "BK1", 1)).thenThrow(new InvalidStateTransitionException(ErrorCode.CHECK_IN_NOT_OPEN,
            "Check-in for this showtime is open from 2026-11-11T13:10 to 2026-11-11T13:45",
            Map.of("startTime", start, "opensAt", start.minusMinutes(15), "closesAt", start.plusMinutes(20))));
        mvc.perform(post("/api/bookings/check-in").header("X-User-Id", "1").header("X-User-Roles", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content("{\"bookingCode\":\"BK1\",\"quantity\":1}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CHECK_IN_NOT_OPEN"))
            .andExpect(jsonPath("$.data.opensAt").value("2026-11-11T13:10:00"))
            .andExpect(jsonPath("$.data.closesAt").value("2026-11-11T13:45:00"))
            .andExpect(jsonPath("$.data.startTime").value("2026-11-11T13:25:00"));
    }

    @Test
    void errorsWithoutAReasonGetTheGenericCodeOfTheirStatus() throws Exception {
        when(checkIn.checkIn(1, "BK1", 1)).thenThrow(new DomainException("Booking code is required"));
        mvc.perform(post("/api/bookings/check-in").header("X-User-Id", "1").header("X-User-Roles", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content("{\"bookingCode\":\"BK1\",\"quantity\":1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.data").doesNotExist());
        when(holdSeats.hold(7, 3L, List.of(101))).thenThrow(new SeatUnavailableException("taken"));
        mvc.perform(post("/api/bookings").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"showtimeId\":3,\"seatIds\":[101]}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"));
        mvc.perform(post("/api/bookings").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"showtimeId\":3}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        when(queries.getBooking(7, false, 404)).thenThrow(new BookingNotFoundException());
        mvc.perform(get("/api/bookings/404").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void aUsedTicketIs409WithItsEarlierScans() throws Exception {
        when(checkIn.checkIn(1, "BK1", null)).thenThrow(new TicketAlreadyUsedException(checkInView(3, 0)));
        mvc.perform(post("/api/bookings/check-in").header("X-User-Id", "1").header("X-User-Roles", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content("{\"bookingCode\":\"BK1\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Every seat of this booking has already been checked in"))
            .andExpect(jsonPath("$.code").value("TICKET_ALREADY_USED"))
            .andExpect(jsonPath("$.data.remaining").value(0))
            .andExpect(jsonPath("$.data.checkIns[0].checkedInAt").value("2027-01-21T18:50:00"));
    }

    @Test
    void ticketQrIsAPngImage() throws Exception {
        when(ticketQr.png(7, false, 11)).thenReturn(new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        mvc.perform(get("/api/bookings/11/qr").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_PNG))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().bytes(new byte[] {(byte) 0x89, 'P', 'N', 'G'}));
    }

    @Test
    void onlyAdminsReRunACancelledShowtime() throws Exception {
        mvc.perform(post("/api/bookings/showtimes/15/cancellation").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(showtimeCancellation);
        when(showtimeCancellation.handle(15L)).thenReturn(new ShowtimeCancellationView(15L, 1, 2, 0));
        mvc.perform(post("/api/bookings/showtimes/15/cancellation").header("X-User-Id", "1").header("X-User-Roles", "ADMIN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.cancelledPending").value(1))
            .andExpect(jsonPath("$.data.cancelledPaid").value(2));
        when(showtimeCancellation.handle(16L)).thenThrow(new InvalidStateTransitionException("not cancelled"));
        mvc.perform(post("/api/bookings/showtimes/16/cancellation").header("X-User-Id", "1").header("X-User-Roles", "ADMIN"))
            .andExpect(status().isConflict());
    }

    private static CheckInView checkInView(int admitted, int remaining) {
        var start = LocalDateTime.of(2027, 1, 21, 19, 0);
        return new CheckInView("BK1", BookingStatus.CONFIRMED, "Dune", "Rạp Hà Nội", "Phòng 1", start,
            List.of(new CheckInView.Seat(101, "A", 1, "Ghế thường")), admitted + remaining, admitted, remaining,
            List.of(new CheckInView.Scan(admitted, start.minusMinutes(10), 1)));
    }

    @Test
    void invalidComboBodiesAre400() throws Exception {
        for (var body : List.of("{}", "{\"combos\":[{\"comboId\":5,\"quantity\":0}]}", "{\"combos\":[{\"quantity\":1}]}")) {
            mvc.perform(put("/api/bookings/11/combos").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    void comboNotOnSaleIs409() throws Exception {
        when(replaceCombos.replace(7, 11, List.of(new ComboLine(5, 1)))).thenThrow(new ComboUnavailableException(5));
        mvc.perform(put("/api/bookings/11/combos").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"combos\":[{\"comboId\":5,\"quantity\":1}]}"))
            .andExpect(status().isConflict());
    }

    @Test
    void missingGatewayIdentityIs401AndInvalidBodyIs400() throws Exception {
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"showtimeId\":3,\"seatIds\":[101]}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/bookings").header("X-User-Id", "7").contentType(MediaType.APPLICATION_JSON)
                .content("{\"showtimeId\":3,\"seatIds\":[]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(holdSeats);
    }

    @Test
    void businessErrorsMapToHttpStatuses() throws Exception {
        when(holdSeats.hold(anyInt(), anyLong(), anyList())).thenThrow(new SeatUnavailableException("taken"));
        mvc.perform(post("/api/bookings").header("X-User-Id", "7").contentType(MediaType.APPLICATION_JSON)
                .content("{\"showtimeId\":3,\"seatIds\":[101]}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("taken"));

        when(initiatePayment.initiate(7, 11)).thenThrow(new InvalidStateTransitionException("expired"));
        mvc.perform(post("/api/bookings/11/payments").header("X-User-Id", "7")).andExpect(status().isConflict());

        when(initiatePayment.initiate(7, 12)).thenThrow(new UpstreamServiceException("down"));
        mvc.perform(post("/api/bookings/12/payments").header("X-User-Id", "7"))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void webhookIsPublicButSignatureChecked() throws Exception {
        mvc.perform(post("/api/payments/payos/webhook").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"00\"}"))
            .andExpect(status().isOk());
        doThrow(new InvalidWebhookException("bad signature")).when(webhook).handle(any());
        mvc.perform(post("/api/payments/payos/webhook").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"00\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void refundEndpointsRequireAdmin() throws Exception {
        mvc.perform(get("/api/payments/refunds").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER"))
            .andExpect(status().isForbidden());
        when(refunds.list(any())).thenReturn(List.of());
        mvc.perform(get("/api/payments/refunds").header("X-User-Id", "1").header("X-User-Roles", "ADMIN"))
            .andExpect(status().isOk());
    }
}
