package fu.mss301.showtime.controller;

import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.showtime.dto.ShowtimeResponseDTO;
import fu.mss301.showtime.enums.ShowtimeStatus;
import fu.mss301.showtime.exception.ShowtimeConflictException;
import fu.mss301.showtime.exception.ShowtimeExceptionHandler;
import fu.mss301.showtime.exception.ShowtimeNotFoundException;
import fu.mss301.showtime.exception.UpstreamServiceException;
import fu.mss301.showtime.service.ShowtimeService;
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

class ShowtimeControllerTests {
    private ShowtimeService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ShowtimeService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ShowtimeController(service))
            .setControllerAdvice(new ShowtimeExceptionHandler()).build();
    }

    @Test
    void defaultPaginationUsesSharedEnvelope() throws Exception {
        when(service.getShowtimes(null, null, null, null, null, null, 0, 10))
            .thenReturn(new PageResponseDTO<>(List.of(), 0, 10, 0, 0, true, true));
        mvc.perform(get("/api/showtimes")).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.data").isArray())
            .andExpect(jsonPath("$.data.page").value(0));
    }

    @Test
    void forwardsFilters() throws Exception {
        mvc.perform(get("/api/showtimes").param("movieId", "1").param("cinemaId", "2")
            .param("screenId", "3").param("date", "2027-01-20").param("location", "Ha Noi")
            .param("status", "SCHEDULED").param("page", "2").param("size", "5"))
            .andExpect(status().isOk());
        verify(service).getShowtimes(1, 2, 3, LocalDate.of(2027, 1, 20), "Ha Noi", ShowtimeStatus.SCHEDULED, 2, 5);
    }

    @Test
    void createReturns201AndLocation() throws Exception {
        when(service.createShowtime(any())).thenReturn(response());
        mvc.perform(post("/api/showtimes").contentType(MediaType.APPLICATION_JSON).content("""
            {"movieId":1,"cinemaId":1,"screenId":1,"startTime":"2027-01-20T18:00:00","basePrice":85000}
            """))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "http://localhost/api/showtimes/7"))
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.data.id").value(7));
        verify(service).createShowtime(argThat(request -> request.movieId() == 1 && request.basePrice().intValue() == 85000));
    }

    @Test
    void invalidJsonMissingBodyAndInvalidParametersReturn400() throws Exception {
        mvc.perform(post("/api/showtimes").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(post("/api/showtimes").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/showtimes/no-id")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/showtimes").param("status", "UNKNOWN")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/showtimes").param("date", "not-a-date")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void missingShowtimeReturns404() throws Exception {
        when(service.getShowtimeById(7L)).thenThrow(new ShowtimeNotFoundException(7L));
        mvc.perform(get("/api/showtimes/7")).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void conflictReturns409() throws Exception {
        when(service.cancelShowtime(7L)).thenThrow(new ShowtimeConflictException("Already started"));
        mvc.perform(post("/api/showtimes/7/cancel")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Already started"));
    }

    @Test
    void upstreamFailureReturns502() throws Exception {
        when(service.createShowtime(any())).thenThrow(new UpstreamServiceException("Cinema service is unavailable"));
        mvc.perform(post("/api/showtimes").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadGateway()).andExpect(jsonPath("$.status").value(502));
    }

    private ShowtimeResponseDTO response() {
        var start = LocalDateTime.of(2027, 1, 20, 18, 0);
        return new ShowtimeResponseDTO(7L, 1, 1, 1, "Ha Noi", start, start.plusHours(2),
            new BigDecimal("85000"), ShowtimeStatus.SCHEDULED, start.minusDays(1), start.minusDays(1));
    }
}
