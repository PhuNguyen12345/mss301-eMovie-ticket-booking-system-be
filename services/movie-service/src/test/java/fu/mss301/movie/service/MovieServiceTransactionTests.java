package fu.mss301.movie.service;

import fu.mss301.movie.entity.Movie;
import fu.mss301.movie.enums.MovieStatus;
import fu.mss301.movie.repository.MovieRepository;
import fu.mss301.movie.repository.MovieGenreRepository;
import fu.mss301.movie.repository.MovieCreditRepository;
import fu.mss301.movie.service.impl.MovieServiceImpl;
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
    void expireMovieUsesWriteTransactionThroughSpringProxy() {
        MovieRepository repository = mock(MovieRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        Movie movie = new Movie();
        movie.setId(1);
        movie.setStatus(MovieStatus.PRESENTING);

        when(repository.findById(1)).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly(),
                "Expiry must override the service's read-only transaction");
            return Optional.of(movie);
        });

        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(new TestTransactionManager());
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(new MovieServiceImpl(repository, entityManager,
            mock(MovieGenreRepository.class), mock(MovieCreditRepository.class)));
        factory.addAdvice(interceptor);
        MovieService service = (MovieService) factory.getProxy();

        service.expireMovie(1);

        assertEquals(MovieStatus.EXPIRED, movie.getStatus());
        verify(repository).flush();
        verify(entityManager).refresh(movie);
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
