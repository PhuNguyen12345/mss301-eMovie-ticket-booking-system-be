package fu.mss301.user.infrastructure.notification;

import fu.mss301.user.infrastructure.email.PasswordResetOtpEmailSender;

import fu.mss301.user.application.event.PasswordResetOtpRequested;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PasswordResetOtpListener {
    private final TaskExecutor executor;
    private final PasswordResetOtpEmailSender sender;

    public PasswordResetOtpListener(@Qualifier("otpMailExecutor") TaskExecutor executor,
                                    PasswordResetOtpEmailSender sender) {
        this.executor = executor;
        this.sender = sender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(PasswordResetOtpRequested event) {
        try {
            executor.execute(() -> sender.send(event));
        } catch (TaskRejectedException exception) {
            sender.markFailed(event);
        }
    }
}
