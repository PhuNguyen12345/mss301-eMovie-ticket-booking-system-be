package fu.mss301.cinema;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.mss301.cinema.application.port.in.CinemaCatalog;
import fu.mss301.cinema.application.port.in.CinemaManagement;
import fu.mss301.cinema.domain.repository.CinemaStore;
import fu.mss301.cinema.infrastructure.config.UseCaseConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;

class CinemaTransactionTests {
    @Test
    void infrastructureWrapsReadsAndWritesAndRollsBackFailures() {
        CinemaStore store = mock(CinemaStore.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(UseCaseConfig.class);
            context.registerBean(CinemaStore.class, () -> store);
            context.registerBean(PlatformTransactionManager.class, () -> manager);
            context.refresh();
            context.getBean(CinemaCatalog.class).listCinemas();
            assertThrows(RuntimeException.class,
                    () -> context.getBean(CinemaManagement.class).createCinema(null, null, null));
            var definitions = ArgumentCaptor.forClass(TransactionDefinition.class);
            verify(manager, times(2)).getTransaction(definitions.capture());
            assertTrue(definitions.getAllValues().get(0).isReadOnly());
            assertFalse(definitions.getAllValues().get(1).isReadOnly());
            verify(manager).commit(any());
            verify(manager).rollback(any());
        }
    }
}
