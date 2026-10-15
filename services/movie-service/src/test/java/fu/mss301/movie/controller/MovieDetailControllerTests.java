package fu.mss301.movie.controller;

import fu.mss301.movie.entity.*;
import fu.mss301.movie.enums.CreditType;
import fu.mss301.movie.enums.MovieStatus;
import fu.mss301.movie.exception.MovieExceptionHandler;
import fu.mss301.movie.repository.MovieCreditRepository;
import fu.mss301.movie.repository.MovieGenreRepository;
import fu.mss301.movie.repository.MovieRepository;
import fu.mss301.movie.service.MoviePosterService;
import fu.mss301.movie.service.impl.MovieServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MovieDetailControllerTests {
    private MovieRepository movies;
    private MovieGenreRepository genres;
    private MovieCreditRepository credits;
    private EntityManager entityManager;
    private MovieServiceImpl service;
    private Movie movie;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        movies = mock(MovieRepository.class);
        genres = mock(MovieGenreRepository.class);
        credits = mock(MovieCreditRepository.class);
        entityManager = mock(EntityManager.class);
        service = new MovieServiceImpl(movies, entityManager, genres, credits);
        mvc = MockMvcBuilders.standaloneSetup(new MovieController(service, mock(MoviePosterService.class)))
            .setControllerAdvice(new MovieExceptionHandler()).build();
        movie = new Movie();
        movie.setId(10);
        movie.setTitle("Omega");
        movie.setDescription("Description");
        movie.setDurationMinutes(120);
        movie.setReleaseDate(LocalDate.of(2026, 10, 15));
        movie.setLanguage("en");
        movie.setAgeRating("T13");
        movie.setPosterUrl("https://example.com/poster.jpg");
        movie.setTrailerUrl("https://example.com/trailer");
        movie.setStatus(MovieStatus.PRESENTING);
        movie.setCreatedAt(LocalDateTime.of(2026, 10, 15, 10, 0));
        movie.setUpdatedAt(LocalDateTime.of(2026, 10, 15, 11, 0));
        when(movies.findById(10)).thenReturn(Optional.of(movie));
        when(genres.findGenresByMovieId(10)).thenReturn(List.of());
        when(credits.findCreditsByMovieId(10)).thenReturn(List.of());
    }

    private MovieGenre genreLink() {
        Genre genre = new Genre();
        genre.setId(1);
        genre.setName("Action");
        genre.setDescription("Action films");
        return new MovieGenre(movie, genre);
    }

    private MovieCredit credit(Person person, CreditType type, int id) {
        MovieCredit credit = new MovieCredit();
        credit.setId(id);
        credit.setMovie(movie);
        credit.setPerson(person);
        credit.setCreditType(type);
        credit.setDisplayOrder(1);
        credit.setCharacterName(type == CreditType.ACTOR ? "Character A" : null);
        return credit;
    }

    @Test
    void detailsIncludeAllBaseFieldsAndBothRolesForSamePerson() throws Exception {
        Person person = new Person();
        person.setId(2);
        person.setFullName("Person A");
        when(genres.findGenresByMovieId(10)).thenReturn(List.of(genreLink()));
        when(credits.findCreditsByMovieId(10)).thenReturn(List.of(
            credit(person, CreditType.DIRECTOR, 20), credit(person, CreditType.ACTOR, 21)
        ));
        mvc.perform(get("/api/movies/10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.id").value(10))
            .andExpect(jsonPath("$.data.title").value("Omega"))
            .andExpect(jsonPath("$.data.description").value("Description"))
            .andExpect(jsonPath("$.data.durationMinutes").value(120))
            .andExpect(jsonPath("$.data.releaseDate").value("2026-10-15"))
            .andExpect(jsonPath("$.data.language").value("en"))
            .andExpect(jsonPath("$.data.ageRating").value("T13"))
            .andExpect(jsonPath("$.data.posterUrl").value(movie.getPosterUrl()))
            .andExpect(jsonPath("$.data.trailerUrl").value(movie.getTrailerUrl()))
            .andExpect(jsonPath("$.data.createdAt").value("2026-10-15T10:00:00"))
            .andExpect(jsonPath("$.data.updatedAt").value("2026-10-15T11:00:00"))
            .andExpect(jsonPath("$.data.genres[0].id").value(1))
            .andExpect(jsonPath("$.data.genres[0].name").value("Action"))
            .andExpect(jsonPath("$.data.credits.length()").value(2))
            .andExpect(jsonPath("$.data.credits[0].id").value(20))
            .andExpect(jsonPath("$.data.credits[0].personId").value(2))
            .andExpect(jsonPath("$.data.credits[0].fullName").value("Person A"))
            .andExpect(jsonPath("$.data.credits[0].creditType").value("DIRECTOR"))
            .andExpect(jsonPath("$.data.credits[1].creditType").value("ACTOR"))
            .andExpect(jsonPath("$.data.credits[1].characterName").value("Character A"))
            .andExpect(jsonPath("$.data.credits[1].displayOrder").value(1));
        verify(movies).findById(10);
        verify(genres).findGenresByMovieId(10);
        verify(credits).findCreditsByMovieId(10);
        verifyNoInteractions(entityManager);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void absentRelationsSerializeAsEmptyArrays(int populatedRelation) throws Exception {
        if (populatedRelation == 1) {
            when(genres.findGenresByMovieId(10)).thenReturn(List.of(genreLink()));
        }
        if (populatedRelation == 2) {
            Person person = new Person();
            person.setId(2);
            when(credits.findCreditsByMovieId(10)).thenReturn(List.of(credit(person, CreditType.ACTOR, 20)));
        }
        mvc.perform(get("/api/movies/10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.genres").isArray())
            .andExpect(jsonPath("$.data.credits").isArray())
            .andExpect(jsonPath("$.data.genres.length()").value(populatedRelation == 1 ? 1 : 0))
            .andExpect(jsonPath("$.data.credits.length()").value(populatedRelation == 2 ? 1 : 0));
    }

    @ParameterizedTest
    @EnumSource(MovieStatus.class)
    void detailsRemainAccessibleForEveryMovieStatus(MovieStatus status) throws Exception {
        movie.setStatus(status);
        mvc.perform(get("/api/movies/10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value(status.name()));
    }

    @Test
    void missingMovieStopsBeforeLoadingRelations() throws Exception {
        mvc.perform(get("/api/movies/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
        verifyNoInteractions(genres, credits);
    }

    @Test
    void invalidIdsDoNotQueryRepositories() throws Exception {
        mvc.perform(get("/api/movies/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/movies/-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/movies/invalid")).andExpect(status().isBadRequest());
        assertThrows(IllegalArgumentException.class, () -> service.getMovieById(null));
        verifyNoInteractions(movies, genres, credits);
    }

    @Test
    void listKeepsCompactResponseWithoutRelationQueries() throws Exception {
        when(movies.searchMovies(isNull(), isNull(), isNull(), any(Pageable.class)))
            .thenAnswer(invocation -> new PageImpl<>(List.of(movie), invocation.getArgument(3), 1));
        mvc.perform(get("/api/movies"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.data[0].title").value("Omega"))
            .andExpect(jsonPath("$.data.data[0].genres").doesNotExist())
            .andExpect(jsonPath("$.data.data[0].credits").doesNotExist());
        verifyNoInteractions(genres, credits);
    }
}
