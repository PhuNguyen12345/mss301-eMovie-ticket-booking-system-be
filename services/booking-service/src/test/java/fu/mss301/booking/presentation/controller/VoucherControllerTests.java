package fu.mss301.booking.presentation.controller;

import fu.mss301.booking.application.booking.BookingPage;
import fu.mss301.booking.application.exception.VoucherNotFoundException;
import fu.mss301.booking.application.voucher.MyVoucherView;
import fu.mss301.booking.application.voucher.VoucherService;
import fu.mss301.booking.application.voucher.VoucherView;
import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.voucher.DiscountType;
import fu.mss301.booking.domain.voucher.VoucherAvailability;
import fu.mss301.booking.domain.voucher.VoucherCodeTakenException;
import fu.mss301.booking.domain.voucher.VoucherKind;
import fu.mss301.booking.domain.voucher.VoucherStatus;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VoucherControllerTests {
    private static final String BODY = "{\"kind\":\"TICKET_DISCOUNT\",\"discountType\":\"FIXED\",\"discountValue\":20000,"
        + "\"startDate\":\"2027-01-01\",\"endDate\":\"2027-01-31\"}";
    private static final LocalDate START = LocalDate.of(2027, 1, 1);
    private static final LocalDate END = LocalDate.of(2027, 1, 31);
    private static final BigDecimal VALUE = new BigDecimal("20000.00");
    private static final VoucherView VIEW = new VoucherView(5, "TET-2027", VoucherKind.TICKET_DISCOUNT, DiscountType.FIXED,
        VALUE, START, END, null, null, null, VoucherStatus.ACTIVE, null, null, null, null, null);
    private static final MyVoucherView MINE = new MyVoucherView(5, "TET-2027", VoucherKind.TICKET_DISCOUNT,
        DiscountType.FIXED, VALUE, START, END, null, null, null, VoucherAvailability.AVAILABLE);

    private VoucherService vouchers;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        vouchers = mock(VoucherService.class);
        mvc = MockMvcBuilders.standaloneSetup(new VoucherController(vouchers))
            .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
            .setControllerAdvice(new BookingExceptionHandler(), new GlobalExceptionHandler())
            .build();
    }

    @Test
    void adminCreatesAVoucher() throws Exception {
        when(vouchers.create(any())).thenReturn(VIEW);
        mvc.perform(post("/api/vouchers").header("X-User-Id", "1").header("X-User-Roles", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/vouchers/5")))
            .andExpect(jsonPath("$.data.code").value("TET-2027"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void customerCannotDoAdminActions() throws Exception {
        mvc.perform(get("/api/vouchers").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/vouchers/5").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/vouchers").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/vouchers/5").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/vouchers/5/deactivate").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/vouchers/5/assign").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":7}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(vouchers);
    }

    @Test
    void adminListsVouchersPageByPage() throws Exception {
        when(vouchers.list(0, 10)).thenReturn(new BookingPage<>(List.of(VIEW), 0, 10, 1, 1, true, true));
        mvc.perform(get("/api/vouchers").header("X-User-Id", "1").header("X-User-Roles", "ADMIN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.data[0].code").value("TET-2027"))
            .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void customerSeesOwnVouchersAndClaimsACode() throws Exception {
        when(vouchers.mine(7)).thenReturn(List.of(MINE));
        when(vouchers.claim(7, "tet-2027")).thenReturn(MINE);
        mvc.perform(get("/api/vouchers/me").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].status").value("AVAILABLE"));
        mvc.perform(post("/api/vouchers/claim").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"tet-2027\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.code").value("TET-2027"));
    }

    @Test
    void anUnusableCodeAnswersNotFound() throws Exception {
        when(vouchers.claim(anyInt(), any())).thenThrow(new VoucherNotFoundException());
        mvc.perform(post("/api/vouchers/claim").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"NOPE-2027\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Voucher not found"));
    }

    @Test
    void duplicateCodeIsAConflict() throws Exception {
        when(vouchers.create(any())).thenThrow(new VoucherCodeTakenException());
        mvc.perform(post("/api/vouchers").header("X-User-Id", "1").header("X-User-Roles", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isConflict());
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        mvc.perform(post("/api/vouchers").header("X-User-Id", "1").header("X-User-Roles", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content("{\"discountValue\":20000}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/vouchers/5/assign").header("X-User-Id", "1").header("X-User-Roles", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":0}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/vouchers/claim").header("X-User-Id", "7").header("X-User-Roles", "CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\" \"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(vouchers);
    }

    @Test
    void domainRulesBecomeBadRequests() throws Exception {
        when(vouchers.create(any())).thenThrow(new DomainException("Bad terms"));
        mvc.perform(post("/api/vouchers").header("X-User-Id", "1").header("X-User-Roles", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isBadRequest());
    }
}
