package fu.mss301.movie.controller;

import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.movie.dto.MovieRequestDTO;
import fu.mss301.movie.dto.MovieResponseDTO;
import fu.mss301.movie.enums.MovieStatus;
import fu.mss301.movie.exception.MovieExceptionHandler;
import fu.mss301.movie.exception.MovieNotFoundException;
import fu.mss301.movie.service.MovieService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MovieControllerTests {

    private MovieService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(MovieService.class);
        mvc = MockMvcBuilders.standaloneSetup(new MovieController(service))
            .setControllerAdvice(new MovieExceptionHandler()).build();
    }

    @Test
    void listUsesDefaultPaginationAndResponseEnvelope() throws Exception {
        when(service.getMovies(null, null, null, 0, 10)).thenReturn(
            new PageResponseDTO<>(List.of(), 0, 10, 0, 0, true, true)
        );
        mvc.perform(get("/api/movies"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.data").isArray())
            .andExpect(jsonPath("$.data.page").value(0));
        verify(service).getMovies(null, null, null, 0, 10);
    }

    @Test
    void listPassesFiltersToService() throws Exception {
        when(service.getMovies("Omega", MovieStatus.PRESENTING, 2, 1, 5))
            .thenReturn(new PageResponseDTO<>(List.of(), 1, 5, 0, 0, false, true));
        mvc.perform(get("/api/movies").param("keyword", "Omega")
            .param("status", "PRESENTING").param("genreId", "2")
            .param("page", "1").param("size", "5"))
            .andExpect(status().isOk());
        verify(service).getMovies("Omega", MovieStatus.PRESENTING, 2, 1, 5);
    }

    @Test
    void createReturns201AndLocation() throws Exception {
        MovieResponseDTO movie = new MovieResponseDTO();
        movie.setId(7);
        movie.setTitle("Omega");
        when(service.createMovie(any(MovieRequestDTO.class))).thenReturn(movie);
        mvc.perform(post("/api/movies").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"title":"Omega","durationMinutes":120,
                 "releaseDate":"2026-10-12","status":"PRESENTING"}
                """))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "http://localhost/api/movies/7"))
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.data.id").value(7));
        verify(service).createMovie(argThat(request ->
            "Omega".equals(request.getTitle()) && request.getDurationMinutes() == 120
        ));
    }

    @Test
    void updatePassesIdAndBodyToService() throws Exception {
        MovieResponseDTO movie = new MovieResponseDTO();
        movie.setId(7);
        when(service.updateMovie(eq(7), any(MovieRequestDTO.class))).thenReturn(movie);
        mvc.perform(put("/api/movies/7").contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"New title\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(7));
        verify(service).updateMovie(eq(7), argThat(request ->
            "New title".equals(request.getTitle())
        ));
    }

    @Test
    void patchStatusDeserializesEnum() throws Exception {
        MovieResponseDTO movie = new MovieResponseDTO();
        movie.setStatus(MovieStatus.EXPIRED);
        when(service.updateMovieStatus(7, MovieStatus.EXPIRED)).thenReturn(movie);
        mvc.perform(patch("/api/movies/7/status").contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"EXPIRED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("EXPIRED"));
        verify(service).updateMovieStatus(7, MovieStatus.EXPIRED);
    }

    @Test
    void expireUsesExistingServiceOperation() throws Exception {
        mvc.perform(post("/api/movies/7/expire"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200));
        verify(service).expireMovie(7);
    }

    @Test
    void missingMovieReturns404Envelope() throws Exception {
        when(service.getMovieById(7)).thenThrow(new MovieNotFoundException(7));
        mvc.perform(get("/api/movies/7"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void serviceValidationReturns400Envelope() throws Exception {
        when(service.getMovies(null, null, null, 0, 101))
            .thenThrow(new IllegalArgumentException("Invalid size"));
        mvc.perform(get("/api/movies").param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Invalid size"));
    }

    @Test
    void invalidEnumAndIdReturn400WithoutCallingService() throws Exception {
        mvc.perform(get("/api/movies").param("status", "UNKNOWN"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
        mvc.perform(get("/api/movies/not-an-id"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void invalidJsonAndMissingBodyReturn400WithoutCallingService() throws Exception {
        mvc.perform(post("/api/movies").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
        mvc.perform(patch("/api/movies/7/status").contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"UNKNOWN\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
        mvc.perform(post("/api/movies").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }
}
