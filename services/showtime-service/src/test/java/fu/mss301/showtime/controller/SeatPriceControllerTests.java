package fu.mss301.showtime.controller;

import fu.mss301.showtime.entity.Showtime;
import fu.mss301.showtime.entity.ShowtimeSeatPrice;
import fu.mss301.showtime.exception.ShowtimeExceptionHandler;
import fu.mss301.showtime.repository.ShowtimeRepository;
import fu.mss301.showtime.repository.ShowtimeSeatPriceRepository;
import fu.mss301.showtime.service.SeatPriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SeatPriceControllerTests {
    private ShowtimeRepository showtimes;
    private ShowtimeSeatPriceRepository prices;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        showtimes = mock(ShowtimeRepository.class);
        prices = mock(ShowtimeSeatPriceRepository.class);
        var service = new SeatPriceService(showtimes, prices, Clock.systemUTC());
        mvc = MockMvcBuilders.standaloneSetup(new SeatPriceController(service))
            .setControllerAdvice(new ShowtimeExceptionHandler()).build();
    }

    @Test
    void returnsPriceRowIdAndBookingContract() throws Exception {
        when(showtimes.existsById(7L)).thenReturn(true);
        var price = new ShowtimeSeatPrice();
        price.setId(77);
        price.setSeatTypeId(2);
        price.setPrice(new BigDecimal("150000"));
        when(prices.findByShowtimeIdOrderBySeatTypeIdAsc(7L)).thenReturn(List.of(price));
        mvc.perform(get("/api/showtimes/7/seat-prices")).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.message").value("Seat prices retrieved successfully"))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].id").value(77))
            .andExpect(jsonPath("$.data[0].seatTypeId").value(2))
            .andExpect(jsonPath("$.data[0].price").value(150000));
    }

    @Test
    void unconfiguredShowtimeReturnsEmptyArray() throws Exception {
        when(showtimes.existsById(7L)).thenReturn(true);
        when(prices.findByShowtimeIdOrderBySeatTypeIdAsc(7L)).thenReturn(List.of());
        mvc.perform(get("/api/showtimes/7/seat-prices")).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200)).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void missingShowtimeReturns404ForReadAndWrite() throws Exception {
        mvc.perform(get("/api/showtimes/7/seat-prices")).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
        mvc.perform(put("/api/showtimes/7/seat-prices").contentType(MediaType.APPLICATION_JSON).content("[]"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        verifyNoInteractions(prices);
    }

    @Test
    void emptyReplacementClearsPrices() throws Exception {
        when(showtimes.findByIdForUpdate(7L)).thenReturn(Optional.of(new Showtime()));
        var oldPrice = new ShowtimeSeatPrice();
        oldPrice.setSeatTypeId(1);
        when(prices.findByShowtimeIdOrderBySeatTypeIdAsc(7L)).thenReturn(List.of(oldPrice), List.of());
        mvc.perform(put("/api/showtimes/7/seat-prices").contentType(MediaType.APPLICATION_JSON).content("[]"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        verify(prices).deleteAll(List.of(oldPrice));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "null", "{}", "[null]", "[{}]", "[{\"seatTypeId\":0,\"price\":1}]",
        "[{\"seatTypeId\":-1,\"price\":1}]", "[{\"seatTypeId\":1}]",
        "[{\"seatTypeId\":1,\"price\":-1}]", "[{\"seatTypeId\":1,\"price\":1.001}]",
        "[{\"seatTypeId\":1,\"price\":10000000000}]",
        "[{\"seatTypeId\":1,\"price\":1},{\"seatTypeId\":1,\"price\":2}]"
    })
    void invalidReplacementReturns400WithoutWrites(String body) throws Exception {
        mvc.perform(put("/api/showtimes/7/seat-prices").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(prices);
    }

    @Test
    void invalidShowtimeIdsReturn400() throws Exception {
        mvc.perform(get("/api/showtimes/0/seat-prices")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/showtimes/no-id/seat-prices")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/showtimes/-1/seat-prices").contentType(MediaType.APPLICATION_JSON).content("[]"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(showtimes, prices);
    }
}
