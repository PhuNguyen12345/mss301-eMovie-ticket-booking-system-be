package fu.mss301.user.infrastructure.messaging.consumer;

import fu.mss301.user.application.projection.EventIngestion;

import fu.mss301.user.domain.event.PasswordChangeOtpRequested;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PasswordChangeOtpListener {
    private final TaskExecutor executor;
    private final EventIngestion ingestion;

    public PasswordChangeOtpListener(@Qualifier("otpMailExecutor") TaskExecutor executor,
                                     EventIngestion ingestion) {
        this.executor = executor;
        this.ingestion = ingestion;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(PasswordChangeOtpRequested event) {
        try {
            executor.execute(() -> ingestion.process(event));
        } catch (TaskRejectedException exception) {
            ingestion.reject(event);
        }
    }
}
