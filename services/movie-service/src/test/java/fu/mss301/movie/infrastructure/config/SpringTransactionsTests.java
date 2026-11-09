package fu.mss301.movie.infrastructure.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.mss301.movie.application.exception.UncertainCommitException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;

class SpringTransactionsTests {
    @Test
    void readsAreReadOnlyAndFailedWritesRollBack() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        var status = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(status);
        var transactions = new SpringTransactions(manager);
        assertEquals("result", transactions.read(() -> "result"));
        var definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(manager).getTransaction(definition.capture());
        assertTrue(definition.getValue().isReadOnly());
        verify(manager).commit(status);
        assertThrows(IllegalStateException.class, () -> transactions.write(() -> {
            throw new IllegalStateException("reject update");
        }));
        verify(manager).rollback(status);
    }

    @Test
    void uncertainCommitIsExposedThroughApplicationException() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        var status = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(status);
        var failure = new TransactionSystemException("connection lost during commit");
        doThrow(failure).when(manager).commit(status);
        var error = assertThrows(UncertainCommitException.class,
                () -> new SpringTransactions(manager).write(() -> "result"));
        assertSame(failure, error.getCause());
    }
}
