package fu.mss301.user.infrastructure.notification;

import fu.mss301.user.application.event.RegistrationOtpRequested;
import fu.mss301.user.infrastructure.email.OtpEmailSender;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RegistrationOtpListener {
    private final TaskExecutor executor;
    private final OtpEmailSender sender;

    public RegistrationOtpListener(@Qualifier("otpMailExecutor") TaskExecutor executor, OtpEmailSender sender) {
        this.executor = executor;
        this.sender = sender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(RegistrationOtpRequested event) {
        try {
            executor.execute(() -> sender.send(event));
        } catch (TaskRejectedException ex) {
            sender.markFailed(event);
        }
    }
}
