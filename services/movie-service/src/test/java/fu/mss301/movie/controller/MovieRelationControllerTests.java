package fu.mss301.movie.controller;

import fu.mss301.movie.entity.*;
import fu.mss301.movie.enums.CreditType;
import fu.mss301.movie.exception.MovieExceptionHandler;
import fu.mss301.movie.exception.MovieRelationExceptionHandler;
import fu.mss301.movie.repository.*;
import fu.mss301.movie.service.MovieRelationService;
import fu.mss301.movie.service.impl.MovieRelationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MovieRelationControllerTests {
    private MovieRepository movies;
    private GenreRepository genres;
    private PersonRepository persons;
    private MovieGenreRepository genreLinks;
    private MovieCreditRepository creditLinks;
    private MockMvc mvc;
    private Movie movie;
    private List<MovieGenre> storedGenres;
    private List<MovieCredit> storedCredits;
    private TestTransactionManager transactions;

    @BeforeEach
    void setUp() {
        movies = mock(MovieRepository.class);
        genres = mock(GenreRepository.class);
        persons = mock(PersonRepository.class);
        genreLinks = mock(MovieGenreRepository.class);
        creditLinks = mock(MovieCreditRepository.class);
        movie = new Movie();
        movie.setId(10);
        storedGenres = new ArrayList<>();
        storedCredits = new ArrayList<>();
        transactions = new TestTransactionManager();
        when(movies.existsById(10)).thenReturn(true);
        when(movies.findByIdForRelationUpdate(10)).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return Optional.of(movie);
        });
        for (int id = 1; id <= 3; id++) {
            Genre genre = new Genre();
            genre.setId(id);
            genre.setName("Genre " + id);
            Person person = new Person();
            person.setId(id);
            person.setFullName("Person " + id);
            when(genres.findByIdForUpdate(id)).thenReturn(Optional.of(genre));
            when(persons.findByIdForUpdate(id)).thenReturn(Optional.of(person));
        }
        when(genreLinks.findGenresByMovieId(10)).thenAnswer(invocation -> List.copyOf(storedGenres));
        when(creditLinks.findCreditsByMovieId(10)).thenAnswer(invocation -> List.copyOf(storedCredits));
        doAnswer(invocation -> {
            Iterable<MovieGenre> removed = invocation.getArgument(0);
            removed.forEach(storedGenres::remove);
            return null;
        }).when(genreLinks).deleteAll(any());
        when(genreLinks.saveAll(any())).thenAnswer(invocation -> {
            Iterable<MovieGenre> added = invocation.getArgument(0);
            added.forEach(storedGenres::add);
            return storedGenres;
        });
        doAnswer(invocation -> {
            Iterable<MovieCredit> removed = invocation.getArgument(0);
            removed.forEach(storedCredits::remove);
            return null;
        }).when(creditLinks).deleteAll(any());
        when(creditLinks.saveAll(any())).thenAnswer(invocation -> {
            Iterable<MovieCredit> added = invocation.getArgument(0);
            added.forEach(credit -> {
                credit.setId(100 + storedCredits.size());
                storedCredits.add(credit);
            });
            return storedCredits;
        });

        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(new MovieRelationServiceImpl(
            movies, genres, persons, genreLinks, creditLinks
        ));
        factory.addAdvice(interceptor);
        MovieRelationService service = (MovieRelationService) factory.getProxy();
        mvc = MockMvcBuilders.standaloneSetup(new MovieRelationController(service))
            .setControllerAdvice(new MovieRelationExceptionHandler(), new MovieExceptionHandler()).build();
    }

    @Test
    void genresCanBeAssignedReplacedRepeatedAndCleared() throws Exception {
        putGenres("{\"genreIds\":[1,2]}");
        MovieGenre retained = storedGenres.get(0);
        putGenres("{\"genreIds\":[1,3]}");
        assertSame(retained, storedGenres.get(0));
        assertEquals(List.of(1, 3), storedGenres.stream().map(link -> link.getGenre().getId()).toList());
        putGenres("{\"genreIds\":[1,3]}");
        assertEquals(2, storedGenres.size());
        mvc.perform(get("/api/movies/10/genres"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].name").value("Genre 1"));
        putGenres("{\"genreIds\":[]}");
        assertTrue(storedGenres.isEmpty());
        assertTrue(transactions.commits > 0);
    }

    @Test
    void missingGenreDoesNotChangeExistingLinksAndRollsBack() throws Exception {
        putGenres("{\"genreIds\":[1]}");
        MovieGenre original = storedGenres.get(0);
        clearInvocations(genreLinks);
        mvc.perform(put("/api/movies/10/genres").contentType(MediaType.APPLICATION_JSON)
            .content("{\"genreIds\":[2,99]}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        assertEquals(List.of(original), storedGenres);
        verify(genreLinks, never()).deleteAll(any());
        verify(genreLinks, never()).saveAll(any());
        assertEquals(1, transactions.rollbacks);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"genreIds\":null}", "{\"genreIds\":[1,1]}",
        "{\"genreIds\":[null]}", "{\"genreIds\":[0]}", "{"})
    void invalidGenreRequestsDoNotWrite(String body) throws Exception {
        mvc.perform(put("/api/movies/10/genres").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verify(genreLinks, never()).saveAll(any());
        verify(genreLinks, never()).deleteAll(any());
    }

    @Test
    void creditsKeepIdsAndSupportSamePersonInTwoRoles() throws Exception {
        putCredits("""
            {"credits":[{"personId":1,"creditType":"ACTOR","characterName":" A ","displayOrder":1}]}
            """);
        MovieCredit retained = storedCredits.get(0);
        Integer retainedId = retained.getId();
        String body = """
            {"credits":[
              {"personId":1,"creditType":"ACTOR","characterName":" B ","displayOrder":2},
              {"personId":1,"creditType":"DIRECTOR","displayOrder":1}]}
            """;
        putCredits(body);
        assertSame(retained, storedCredits.get(0));
        assertEquals(retainedId, retained.getId());
        assertEquals("B", retained.getCharacterName());
        assertEquals(2, retained.getDisplayOrder());
        putCredits(body);
        assertEquals(2, storedCredits.size());
        mvc.perform(get("/api/movies/10/credits"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].fullName").value("Person 1"));
        putCredits("{\"credits\":[]}");
        assertTrue(storedCredits.isEmpty());
    }

    @Test
    void creditReplacementRemovesAbsentPersonAndAddsNewPerson() throws Exception {
        putCredits("{\"credits\":[{\"personId\":1,\"creditType\":\"ACTOR\",\"displayOrder\":1}]}");
        putCredits("{\"credits\":[{\"personId\":2,\"creditType\":\"ACTOR\",\"displayOrder\":1}]}");
        assertEquals(1, storedCredits.size());
        assertEquals(2, storedCredits.get(0).getPerson().getId());
    }

    @Test
    void missingPersonDoesNotMutateExistingCredit() throws Exception {
        putCredits("{\"credits\":[{\"personId\":1,\"creditType\":\"ACTOR\",\"displayOrder\":1}]}");
        MovieCredit original = storedCredits.get(0);
        mvc.perform(put("/api/movies/10/credits").contentType(MediaType.APPLICATION_JSON).content("""
            {"credits":[
              {"personId":1,"creditType":"ACTOR","characterName":"changed","displayOrder":2},
              {"personId":99,"creditType":"ACTOR","displayOrder":1}]}
            """))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        assertEquals(List.of(original), storedCredits);
        assertNull(original.getCharacterName());
        assertEquals(1, original.getDisplayOrder());
        assertEquals(1, transactions.rollbacks);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"credits\":null}", "{\"credits\":[null]}",
        "{\"credits\":[{}]}",
        "{\"credits\":[{\"personId\":1,\"displayOrder\":1}]}",
        "{\"credits\":[{\"personId\":1,\"creditType\":\"ACTOR\"}]}",
        "{\"credits\":[{\"personId\":1,\"creditType\":\"ACTOR\",\"displayOrder\":0}]}",
        "{\"credits\":[{\"personId\":1,\"creditType\":\"UNKNOWN\",\"displayOrder\":1}]}",
        "{\"credits\":[{\"personId\":1,\"creditType\":\"DIRECTOR\",\"characterName\":\"A\",\"displayOrder\":1}]}"})
    void invalidCreditsReturn400(String body) throws Exception {
        mvc.perform(put("/api/movies/10/credits").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verify(creditLinks, never()).saveAll(any());
        verify(creditLinks, never()).deleteAll(any());
    }

    @Test
    void duplicateCreditAndLongCharacterNameAreRejected() throws Exception {
        String item = "{\"personId\":1,\"creditType\":\"ACTOR\",\"displayOrder\":1}";
        mvc.perform(put("/api/movies/10/credits").contentType(MediaType.APPLICATION_JSON)
            .content("{\"credits\":[" + item + "," + item + "]}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/movies/10/credits").contentType(MediaType.APPLICATION_JSON)
            .content("{\"credits\":[{\"personId\":1,\"creditType\":\"ACTOR\",\"displayOrder\":1,\"characterName\":\""
                + "a".repeat(256) + "\"}]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void missingMovieAndInvalidIdUseConsistentErrors() throws Exception {
        for (String path : List.of("genres", "credits")) {
            mvc.perform(get("/api/movies/99/" + path)).andExpect(status().isNotFound());
            mvc.perform(get("/api/movies/0/" + path)).andExpect(status().isBadRequest());
            mvc.perform(put("/api/movies/99/" + path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"" + (path.equals("genres") ? "genreIds" : "credits") + "\":[]}"))
                .andExpect(status().isNotFound());
        }
    }

    @Test
    void existingDuplicateCreditsReturn409WithoutDeletingData() throws Exception {
        Person person = new Person();
        person.setId(1);
        for (int id : List.of(1, 2)) {
            MovieCredit credit = new MovieCredit();
            credit.setId(id);
            credit.setPerson(person);
            credit.setCreditType(CreditType.ACTOR);
            storedCredits.add(credit);
        }
        mvc.perform(put("/api/movies/10/credits").contentType(MediaType.APPLICATION_JSON)
            .content("{\"credits\":[]}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        assertEquals(2, storedCredits.size());
    }

    @Test
    void lockConflictReturns409AndRollsBack() throws Exception {
        doThrow(new CannotAcquireLockException("locked")).when(movies).findByIdForRelationUpdate(10);
        mvc.perform(put("/api/movies/10/genres").contentType(MediaType.APPLICATION_JSON)
            .content("{\"genreIds\":[]}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        assertEquals(1, transactions.rollbacks);
    }

    private void putGenres(String body) throws Exception {
        mvc.perform(put("/api/movies/10/genres").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(200));
    }

    private void putCredits(String body) throws Exception {
        mvc.perform(put("/api/movies/10/credits").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(200));
    }

    // Checks interception/rollback decisions; repository state is simulated, not a DB.
    private static class TestTransactionManager extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
