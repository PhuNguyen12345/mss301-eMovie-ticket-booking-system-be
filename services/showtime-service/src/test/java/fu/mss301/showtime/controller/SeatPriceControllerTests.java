package fu.mss301.showtime.controller;

import fu.mss301.showtime.presentation.controller.*;

import fu.mss301.showtime.domain.model.*;
import fu.mss301.showtime.domain.repository.ShowtimeRepository;
import fu.mss301.showtime.application.port.Transactions;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import fu.mss301.showtime.presentation.exception.ShowtimeExceptionHandler;
import fu.mss301.showtime.application.usecase.SeatPriceService;
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
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        showtimes = mock(ShowtimeRepository.class);
        when(showtimes.findByIdForUpdate(7L)).thenAnswer(call -> Optional.of(showtime(List.of())));
        when(showtimes.save(any())).thenAnswer(call -> call.getArgument(0));
        var transactions = new Transactions() {
            public <T> T read(Supplier<T> work) { return work.get(); }
            public <T> T write(Supplier<T> work) { return work.get(); }
        };
        var service = new SeatPriceService(showtimes, transactions, Clock.systemUTC());
        mvc = MockMvcBuilders.standaloneSetup(new SeatPriceController(service))
            .setControllerAdvice(new ShowtimeExceptionHandler()).build();
    }

    @Test
    void returnsPriceRowIdAndBookingContract() throws Exception {
        var now = LocalDateTime.now();
        var price = new SeatPrice(77, 2, new Price(new BigDecimal("150000")), now, now);
        when(showtimes.findById(7L)).thenReturn(Optional.of(showtime(List.of(price))));
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
        when(showtimes.findById(7L)).thenReturn(Optional.of(showtime(List.of())));
        mvc.perform(get("/api/showtimes/7/seat-prices")).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200)).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void missingShowtimeReturns404ForReadAndWrite() throws Exception {
        when(showtimes.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/showtimes/7/seat-prices")).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
        mvc.perform(put("/api/showtimes/7/seat-prices").contentType(MediaType.APPLICATION_JSON).content("[]"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        verify(showtimes, never()).save(any());
    }

    @Test
    void emptyReplacementClearsPrices() throws Exception {
        var now = LocalDateTime.now();
        var oldPrice = new SeatPrice(77, 1, new Price(BigDecimal.TEN), now, now);
        when(showtimes.findByIdForUpdate(7L)).thenReturn(Optional.of(showtime(List.of(oldPrice))));
        mvc.perform(put("/api/showtimes/7/seat-prices").contentType(MediaType.APPLICATION_JSON).content("[]"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        verify(showtimes).save(argThat(showtime -> showtime.seatPrices().isEmpty()));
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
        verify(showtimes, never()).save(any());
    }

    @Test
    void invalidShowtimeIdsReturn400() throws Exception {
        mvc.perform(get("/api/showtimes/0/seat-prices")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/showtimes/no-id/seat-prices")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/showtimes/-1/seat-prices").contentType(MediaType.APPLICATION_JSON).content("[]"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(showtimes);
    }

    private Showtime showtime(List<SeatPrice> prices) {
        var start = LocalDateTime.of(2027, 1, 20, 18, 0);
        return Showtime.restore(7L, new Schedule(1, 1, 1, "Ha Noi", start, start.plusHours(2),
            new Price(BigDecimal.TEN)), ShowtimeStatus.SCHEDULED, start.minusDays(1), start.minusDays(1), prices);
    }
}
