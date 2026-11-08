package fu.mss301.booking.presentation.controller;

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
import fu.mss301.booking.presentation.exception.BookingExceptionHandler;
import fu.mss301.booking.presentation.security.CurrentUserArgumentResolver;
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
        mvc = MockMvcBuilders.standaloneSetup(
                new BookingController(mock(SeatMapQuery.class), holdSeats, queries,
                    mock(CancelBookingUseCase.class), initiatePayment, replaceCombos, vouchers, checkIn, ticketQr),
                new PaymentController(webhook, mock(ReconcilePaymentUseCase.class), refunds))
            .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
            .setControllerAdvice(new BookingExceptionHandler(), new GlobalExceptionHandler())
            .build();
    }

    @Test
    void holdReturns201WithLocation() throws Exception {
        when(holdSeats.hold(7, 3L, List.of(101, 102))).thenReturn(new BookingView(11, "BK260101000000ABCDEF", 7, 3L,
            BookingStatus.PENDING_PAYMENT, new BigDecimal("180000.00"), BigDecimal.ZERO, new BigDecimal("180000.00"),
            LocalDateTime.now(), LocalDateTime.now(), null, List.of(), List.of(), List.of(), 0, null));
        mvc.perform(post("/api/bookings").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"showtimeId\":3,\"seatIds\":[101,102]}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "http://localhost/api/bookings/11"))
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
            List.of(new BookingView.ComboItem(5, "Bắp nước", 2, new BigDecimal("85000.00"))), List.of(), 0, null));
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
            0, null));
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
            .andExpect(status().isNotFound());
    }

    @Test
    void removingAVoucherUsesDelete() throws Exception {
        when(vouchers.remove(7, 11, 9)).thenThrow(new InvalidStateTransitionException("link open"));
        mvc.perform(delete("/api/bookings/11/vouchers/9").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER"))
            .andExpect(status().isConflict());
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
    void aUsedTicketIs409WithItsEarlierScans() throws Exception {
        when(checkIn.checkIn(1, "BK1", null)).thenThrow(new TicketAlreadyUsedException(checkInView(3, 0)));
        mvc.perform(post("/api/bookings/check-in").header("X-User-Id", "1").header("X-User-Roles", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content("{\"bookingCode\":\"BK1\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Every seat of this booking has already been checked in"))
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
