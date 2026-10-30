package fu.mss301.booking.presentation.controller;

import fu.mss301.booking.application.booking.*;
import fu.mss301.booking.application.exception.InvalidWebhookException;
import fu.mss301.booking.application.exception.SeatUnavailableException;
import fu.mss301.booking.application.exception.UpstreamServiceException;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BookingControllerTests {
    private HoldSeatsUseCase holdSeats;
    private InitiatePaymentUseCase initiatePayment;
    private HandlePayosWebhookUseCase webhook;
    private RefundAdminUseCase refunds;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        holdSeats = mock(HoldSeatsUseCase.class);
        initiatePayment = mock(InitiatePaymentUseCase.class);
        webhook = mock(HandlePayosWebhookUseCase.class);
        refunds = mock(RefundAdminUseCase.class);
        mvc = MockMvcBuilders.standaloneSetup(
                new BookingController(mock(SeatMapQuery.class), holdSeats, mock(BookingQueryService.class),
                    mock(CancelBookingUseCase.class), initiatePayment),
                new PaymentController(webhook, mock(ReconcilePaymentUseCase.class), refunds))
            .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
            .setControllerAdvice(new BookingExceptionHandler(), new GlobalExceptionHandler())
            .build();
    }

    @Test
    void holdReturns201WithLocation() throws Exception {
        when(holdSeats.hold(7, 3L, List.of(101, 102))).thenReturn(new BookingView(11, "BK260101000000ABCDEF", 7, 3L,
            BookingStatus.PENDING_PAYMENT, new BigDecimal("180000.00"), LocalDateTime.now(), LocalDateTime.now(),
            List.of(), null));
        mvc.perform(post("/api/bookings").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"showtimeId\":3,\"seatIds\":[101,102]}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "http://localhost/api/bookings/11"))
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"));
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
