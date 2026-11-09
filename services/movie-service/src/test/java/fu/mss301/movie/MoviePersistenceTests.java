package fu.mss301.movie;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.mss301.movie.application.dto.*;
import fu.mss301.movie.application.port.in.*;
import fu.mss301.movie.application.port.out.*;
import fu.mss301.movie.application.usecase.MoviePosterService;
import fu.mss301.movie.domain.model.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(properties = {
    "spring.datasource.url=${MOVIE_TEST_DB_URL}",
    "spring.datasource.username=${MOVIE_TEST_DB_USERNAME:root}",
    "spring.datasource.password=${MOVIE_TEST_DB_PASSWORD:architecture_test}",
    "cloudinary.cloud-name=test", "cloudinary.api-key=test", "cloudinary.api-secret=test",
    "eureka.client.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "MOVIE_TEST_DB_URL", matches = ".+")
class MoviePersistenceTests {
    @Autowired MovieService movies;
    @Autowired GenreService genres;
    @Autowired PersonService persons;
    @Autowired MovieRelationService relations;
    @Autowired MoviePosterService posters;
    @Autowired MovieCatalog catalog;
    @Autowired Transactions transactions;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ImageStorage storage;

    @BeforeEach
    void clearTestDatabase() {
        jdbc.update("DELETE FROM movie_credit");
        jdbc.update("DELETE FROM movie_genre");
        jdbc.update("DELETE FROM movie");
        jdbc.update("DELETE FROM genre");
        jdbc.update("DELETE FROM person");
    }

    @Test
    void metadataAndRelationsRoundTripWithStableCreditIds() {
        var movie = movies.createMovie(request("Original"));
        assertNotNull(movie.getCreatedAt());
        int id = movie.getId();
        int genre = genres.createGenre(new GenreRequestDTO("Drama", null)).getId();
        int actor = persons.createPerson(new PersonRequestDTO("Actor")).getId();
        relations.replaceGenres(id, new MovieGenresRequestDTO(List.of(genre)));
        var initial = relations.replaceCredits(id, credits(actor, "First", 1)).getFirst();
        var changed = relations.replaceCredits(id, credits(actor, "Second", 2)).getFirst();
        assertEquals(initial.getId(), changed.getId());
        assertEquals("Second", changed.getCharacterName());
        movies.updateMovie(id, request("Renamed"));
        movies.expireMovie(id);
        var detail = movies.getMovieById(id);
        assertEquals("Renamed", detail.getTitle());
        assertEquals(MovieStatus.EXPIRED, detail.getStatus());
        assertEquals(genre, detail.getGenres().getFirst().getId());
        assertEquals(initial.getId(), detail.getCredits().getFirst().getId());
        relations.replaceGenres(id, new MovieGenresRequestDTO(List.of()));
        relations.replaceCredits(id, new MovieCreditsRequestDTO(List.of()));
        assertTrue(movies.getMovieById(id).getCredits().isEmpty());
        assertTrue(movies.getMovieById(id).getGenres().isEmpty());
    }

    @Test
    void failedTransactionRollsBackDomainChanges() {
        int id = movies.createMovie(request("Original")).getId();
        assertThrows(IllegalStateException.class, () -> transactions.write(() -> {
            var movie = catalog.find(id, true);
            movie.changeStatus(MovieStatus.EXPIRED);
            catalog.saveDetails(movie);
            throw new IllegalStateException("abort");
        }));
        assertEquals(MovieStatus.PRESENTING, movies.getMovieById(id).getStatus());
    }

    @Test
    void posterUploadRunsOutsideTransactionAndDeletesOldAssetAfterCommit() throws Exception {
        int id = movies.createMovie(request("Poster")).getId();
        when(storage.uploadImage(any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new ImageUploadResult("https://example.test/first.jpg", "first");
        });
        posters.uploadPoster(id, new byte[] {1});
        when(storage.uploadImage(any())).thenReturn(new ImageUploadResult("https://example.test/second.jpg", "second"));
        doAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("https://example.test/second.jpg", movies.getMovieById(id).getPosterUrl());
            return null;
        }).when(storage).deleteImage("first");
        posters.uploadPoster(id, new byte[] {2});
        verify(storage).deleteImage("first");
        assertEquals("second", jdbc.queryForObject("SELECT poster_public_id FROM movie WHERE movie_id = ?", String.class, id));
    }

    private MovieRequestDTO request(String title) {
        return new MovieRequestDTO(title, null, 120, LocalDate.of(2026, 11, 8),
                null, null, null, null, MovieStatus.PRESENTING);
    }

    private MovieCreditsRequestDTO credits(int person, String character, int order) {
        return new MovieCreditsRequestDTO(List.of(new MovieCreditRequestDTO(person, CreditType.ACTOR, character, order)));
    }
}
