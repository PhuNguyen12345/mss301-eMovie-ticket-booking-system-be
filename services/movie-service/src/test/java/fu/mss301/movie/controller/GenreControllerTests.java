package fu.mss301.movie.controller;

import fu.mss301.movie.entity.Genre;
import fu.mss301.movie.exception.GenreExceptionHandler;
import fu.mss301.movie.exception.MovieExceptionHandler;
import fu.mss301.movie.repository.GenreRepository;
import fu.mss301.movie.repository.MovieGenreRepository;
import fu.mss301.movie.service.impl.GenreServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GenreControllerTests {
    private GenreRepository genres;
    private MovieGenreRepository links;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        genres = mock(GenreRepository.class);
        links = mock(MovieGenreRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(
            new GenreController(new GenreServiceImpl(genres, links))
        ).setControllerAdvice(new GenreExceptionHandler(), new MovieExceptionHandler()).build();
    }

    private Genre genre() {
        Genre genre = new Genre();
        genre.setId(1);
        genre.setName("Action");
        genre.setDescription("Original description");
        return genre;
    }

    @Test
    void createTrimsNameAndReturns201WithLocation() throws Exception {
        when(genres.saveAndFlush(any(Genre.class))).thenAnswer(invocation -> {
            Genre genre = invocation.getArgument(0);
            genre.setId(1);
            return genre;
        });
        mvc.perform(post("/api/genres").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"  Action  \",\"description\":\"Films\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "http://localhost/api/genres/1"))
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.data.name").value("Action"));
        verify(genres).existsByNameIgnoreCase("Action");
    }

    @Test
    void duplicateNameReturns409WithoutSaving() throws Exception {
        when(genres.existsByNameIgnoreCase("action")).thenReturn(true);
        mvc.perform(post("/api/genres").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"action\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
        verify(genres, never()).saveAndFlush(any());
    }

    @Test
    void databaseConstraintConflictAlsoReturns409() throws Exception {
        when(genres.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        mvc.perform(post("/api/genres").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Action\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void updateAllowsOwnNameAndClearsOmittedDescription() throws Exception {
        Genre genre = genre();
        when(genres.findById(1)).thenReturn(Optional.of(genre));
        mvc.perform(put("/api/genres/1").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Action\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Action"));
        verify(genres).existsByNameIgnoreCaseAndIdNot("Action", 1);
        verify(genres).flush();
        assertNull(genre.getDescription());
    }

    @Test
    void updateRejectsNameUsedByAnotherGenreWithoutMutatingEntity() throws Exception {
        Genre genre = genre();
        when(genres.findById(1)).thenReturn(Optional.of(genre));
        when(genres.existsByNameIgnoreCaseAndIdNot("Drama", 1)).thenReturn(true);
        mvc.perform(put("/api/genres/1").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Drama\"}"))
            .andExpect(status().isConflict());
        assertEquals("Action", genre.getName());
        verify(genres, never()).flush();
    }

    @Test
    void cannotDeleteGenreUsedByMovie() throws Exception {
        when(genres.findById(1)).thenReturn(Optional.of(genre()));
        when(links.existsByGenre_Id(1)).thenReturn(true);
        mvc.perform(delete("/api/genres/1"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
        verify(genres, never()).delete(any(Genre.class));
    }

    @Test
    void deleteUnusedGenreReturns200() throws Exception {
        Genre genre = genre();
        when(genres.findById(1)).thenReturn(Optional.of(genre));
        mvc.perform(delete("/api/genres/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200));
        verify(genres).delete(genre);
        verify(genres).flush();
    }

    @Test
    void listReturnsPaginationAndUsesNormalizedSearch() throws Exception {
        when(genres.findByNameContainingIgnoreCase(eq("Act"), any(Pageable.class)))
            .thenAnswer(invocation -> new PageImpl<>(List.of(genre()), invocation.getArgument(1), 1));
        mvc.perform(get("/api/genres").param("keyword", " Act "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.data[0].name").value("Action"))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(10))
            .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void blankKeywordUsesUnfilteredList() throws Exception {
        when(genres.findAll(any(Pageable.class)))
            .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(0), 0));
        mvc.perform(get("/api/genres").param("keyword", " "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.data").isEmpty());
        verify(genres, never()).findByNameContainingIgnoreCase(anyString(), any());
    }

    @Test
    void detailsAndMissingIdHaveCorrectStatus() throws Exception {
        when(genres.findById(1)).thenReturn(Optional.of(genre()));
        mvc.perform(get("/api/genres/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(1));
        mvc.perform(get("/api/genres/2"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\" \"}", "{\"name\":null}", "{"})
    void invalidBodyReturns400(String body) throws Exception {
        mvc.perform(post("/api/genres").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(genres, links);
    }

    @Test
    void lengthLimitsAreValidated() throws Exception {
        mvc.perform(post("/api/genres").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"" + "a".repeat(101) + "\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/genres").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Action\",\"description\":\"" + "a".repeat(501) + "\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(genres, links);
    }

    @Test
    void invalidPaginationAndIdReturn400() throws Exception {
        mvc.perform(get("/api/genres").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/genres").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/genres").param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/genres/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/genres/abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(genres, links);
    }
}
