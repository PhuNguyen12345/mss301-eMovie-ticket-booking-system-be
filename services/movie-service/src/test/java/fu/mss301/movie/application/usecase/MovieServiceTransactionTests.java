package fu.mss301.movie.application.usecase;

import fu.mss301.movie.application.port.in.MovieService;

import fu.mss301.movie.infrastructure.persistence.entity.Movie;
import fu.mss301.movie.domain.model.MovieStatus;
import fu.mss301.movie.infrastructure.persistence.repository.MovieRepository;
import fu.mss301.movie.infrastructure.persistence.repository.MovieGenreRepository;
import fu.mss301.movie.infrastructure.persistence.repository.MovieCreditRepository;
import fu.mss301.movie.application.usecase.MovieUseCase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MovieServiceTransactionTests {

    @Test
    void expireMovieUsesWriteTransactionThroughInfrastructureAdapter() {
        var catalog = mock(fu.mss301.movie.application.port.out.MovieCatalog.class);
        var movie = fu.mss301.movie.domain.model.Movie.restore(1,
            new fu.mss301.movie.domain.model.MovieDetails("Film", null, 90, java.time.LocalDate.now(),
                null, null, null, null, MovieStatus.PRESENTING), null, null, null, java.util.List.of(), java.util.List.of());
        when(catalog.find(1, false)).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return movie;
        });
        when(catalog.saveDetails(movie)).thenReturn(movie);
        var service = new MovieUseCase(catalog, new fu.mss301.movie.infrastructure.config.SpringTransactions(new TestTransactionManager()));
        service.expireMovie(1);
        assertEquals(MovieStatus.EXPIRED, movie.details().status());
        verify(catalog).saveDetails(movie);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    // Exercises Spring transaction interception without opening a database connection.
    private static class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }
}
