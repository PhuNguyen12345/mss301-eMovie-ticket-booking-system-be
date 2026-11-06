package fu.mss301.user.infrastructure.messaging.consumer;

import fu.mss301.user.domain.event.RegistrationOtpRequested;
import fu.mss301.user.application.projection.EventIngestion;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RegistrationOtpListener {
    private final TaskExecutor executor;
    private final EventIngestion ingestion;

    public RegistrationOtpListener(@Qualifier("otpMailExecutor") TaskExecutor executor, EventIngestion ingestion) {
        this.executor = executor;
        this.ingestion = ingestion;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(RegistrationOtpRequested event) {
        try {
            executor.execute(() -> ingestion.process(event));
        } catch (TaskRejectedException ex) {
            ingestion.reject(event);
        }
    }
}
