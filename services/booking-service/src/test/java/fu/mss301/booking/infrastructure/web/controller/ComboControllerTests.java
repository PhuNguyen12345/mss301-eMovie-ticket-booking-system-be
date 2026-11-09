package fu.mss301.booking.infrastructure.web.controller;

import fu.mss301.booking.application.combo.ComboService;
import fu.mss301.booking.application.combo.ComboView;
import fu.mss301.booking.application.exception.ComboNotFoundException;
import fu.mss301.booking.domain.combo.ComboStatus;
import fu.mss301.booking.infrastructure.web.exception.BookingExceptionHandler;
import fu.mss301.booking.infrastructure.web.security.CurrentUserArgumentResolver;
import fu.mss301.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ComboControllerTests {
    private static final String BODY = "{\"name\":\"Bắp nước\",\"description\":\"1 bắp\",\"price\":85000}";
    private static final ComboView VIEW = new ComboView(5, "Bắp nước", "1 bắp", new BigDecimal("85000.00"), ComboStatus.ACTIVE);

    private ComboService combos;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        combos = mock(ComboService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ComboController(combos))
            .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
            .setControllerAdvice(new BookingExceptionHandler(), new GlobalExceptionHandler())
            .build();
    }

    @Test
    void menuIsPublic() throws Exception {
        when(combos.listOnSale()).thenReturn(List.of(VIEW));
        mvc.perform(get("/api/combos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("Bắp nước"))
            .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    @Test
    void adminCreatesACombo() throws Exception {
        when(combos.create(any())).thenReturn(VIEW);
        mvc.perform(post("/api/combos").header("X-User-Id", "1").header("X-User-Roles", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "http://localhost/api/combos/5"))
            .andExpect(jsonPath("$.data.id").value(5));
    }

    @Test
    void customersCannotManageCombos() throws Exception {
        var customer = new String[] {"X-User-Id", "7", "X-User-Roles", "CUSTOMER"};
        mvc.perform(get("/api/combos/all").header(customer[0], customer[1]).header(customer[2], customer[3]))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/combos").header(customer[0], customer[1]).header(customer[2], customer[3])
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/combos/5").header(customer[0], customer[1]).header(customer[2], customer[3])
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/combos/5/deactivate").header(customer[0], customer[1]).header(customer[2], customer[3]))
            .andExpect(status().isForbidden());
        verifyNoInteractions(combos);
    }

    @Test
    void invalidBodyIs400() throws Exception {
        mvc.perform(post("/api/combos").header("X-User-Id", "1").header("X-User-Roles", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"price\":0}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void missingComboIs404() throws Exception {
        when(combos.deactivate(9)).thenThrow(new ComboNotFoundException());
        mvc.perform(post("/api/combos/9/deactivate").header("X-User-Id", "1").header("X-User-Roles", "ADMIN"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Combo not found"));
    }
}
