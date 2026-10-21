package fu.mss301.user.infrastructure.notification;

import fu.mss301.user.infrastructure.email.PasswordChangeOtpEmailSender;

import fu.mss301.user.application.event.PasswordChangeOtpRequested;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PasswordChangeOtpListener {
    private final TaskExecutor executor;
    private final PasswordChangeOtpEmailSender sender;

    public PasswordChangeOtpListener(@Qualifier("otpMailExecutor") TaskExecutor executor,
                                     PasswordChangeOtpEmailSender sender) {
        this.executor = executor;
        this.sender = sender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(PasswordChangeOtpRequested event) {
        try {
            executor.execute(() -> sender.send(event));
        } catch (TaskRejectedException exception) {
            sender.markFailed(event);
        }
    }
}
