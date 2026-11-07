package fu.mss301.user.infrastructure.messaging.consumer;

import fu.mss301.user.application.projection.EventIngestion;

import fu.mss301.user.domain.event.RegistrationOtpRequested;

import static org.mockito.Mockito.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RegistrationOtpListenerTest {
    @Test
    void queuesMailOnlyAfterCommit() {
        assertCompletion(TransactionSynchronization.STATUS_COMMITTED, true);
    }

    @Test
    void doesNotQueueMailOnRollback() {
        assertCompletion(TransactionSynchronization.STATUS_ROLLED_BACK, false);
    }

    private void assertCompletion(int status, boolean shouldSend) {
        TaskExecutor executor = mock(TaskExecutor.class);
        EventIngestion ingestion = mock(EventIngestion.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(TransactionalEventListenerFactory.class);
            context.registerBean(RegistrationOtpListener.class, () -> new RegistrationOtpListener(executor, ingestion));
            context.refresh();
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                context.publishEvent(new RegistrationOtpRequested("id", "person@example.com", "012345", "hash", Instant.now()));
                verifyNoInteractions(executor, ingestion);
                for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                    synchronization.afterCompletion(status);
                }
                if (shouldSend) {
                    verify(executor).execute(any(Runnable.class));
                } else {
                    verifyNoInteractions(executor, ingestion);
                }
            } finally {
                TransactionSynchronizationManager.clear();
            }
        }
    }

    @Test
    void recordsRejectedWorkAsFailed() {
        TaskExecutor executor = mock(TaskExecutor.class);
        EventIngestion ingestion = mock(EventIngestion.class);
        doThrow(new TaskRejectedException("Queue full")).when(executor).execute(any(Runnable.class));
        var event = new RegistrationOtpRequested("id", "person@example.com", "012345", "hash", Instant.now());
        new RegistrationOtpListener(executor, ingestion).onRequested(event);
        verify(ingestion).reject(event);
    }
}
