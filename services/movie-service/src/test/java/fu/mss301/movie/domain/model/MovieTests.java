package fu.mss301.movie.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import fu.mss301.movie.domain.exception.MovieRelationConflictException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MovieTests {
    private MovieDetails details(String title) {
        return new MovieDetails(title, null, 120, LocalDate.of(2026, 11, 8),
                "Vietnamese", null, null, null, MovieStatus.PRESENTING);
    }

    @Test
    void invalidRevisionLeavesMovieUnchanged() {
        Movie movie = Movie.create(details(" Original "));
        assertThrows(IllegalArgumentException.class, () -> movie.revise(details(" ")));
        assertEquals("Original", movie.details().title());
        movie.changeStatus(MovieStatus.EXPIRED);
        assertEquals(MovieStatus.EXPIRED, movie.details().status());
        assertThrows(IllegalArgumentException.class, () -> movie.changeStatus(null));
        assertEquals(MovieStatus.EXPIRED, movie.details().status());
    }

    @Test
    void genreReplacementIsValidatedBeforeMutationAndCannotBeEditedThroughGetter() {
        Movie movie = Movie.create(details("Movie"));
        movie.replaceGenres(List.of(3, 1));
        assertEquals(List.of(1, 3), movie.genreIds());
        assertThrows(IllegalArgumentException.class, () -> movie.replaceGenres(List.of(2, 2)));
        assertEquals(List.of(1, 3), movie.genreIds());
        assertThrows(UnsupportedOperationException.class, () -> movie.genreIds().clear());
    }

    @Test
    void replacingCreditsRetainsIdentityForSamePersonAndRole() {
        Movie movie = Movie.restore(1, details("Movie"), null, null, null, List.of(),
                List.of(new Credit(10, 2, CreditType.ACTOR, "Old", 1)));
        movie.replaceCredits(List.of(new Credit(null, 2, CreditType.ACTOR, " New ", 2),
                new Credit(null, 3, CreditType.DIRECTOR, null, 1)));
        assertEquals(10, movie.credits().getFirst().id());
        assertEquals("New", movie.credits().getFirst().characterName());
        assertNull(movie.credits().getLast().id());
        var previous = movie.credits();
        assertThrows(IllegalArgumentException.class, () -> movie.replaceCredits(
                List.of(new Credit(null, 3, CreditType.DIRECTOR, "Character", 1))));
        assertEquals(previous, movie.credits());
    }

    @Test
    void duplicateExistingCreditsCannotBeSilentlyDiscarded() {
        var duplicates = List.of(new Credit(10, 2, CreditType.ACTOR, null, 1),
                new Credit(11, 2, CreditType.ACTOR, null, 2));
        Movie movie = Movie.restore(1, details("Movie"), null, null, null, List.of(), duplicates);
        assertThrows(MovieRelationConflictException.class, () -> movie.replaceCredits(List.of()));
        assertEquals(duplicates, movie.credits());
    }
}
