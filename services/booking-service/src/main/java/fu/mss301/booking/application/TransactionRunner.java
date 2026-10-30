package fu.mss301.booking.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Runs one unit of work in its own transaction. Use cases call it once per aggregate so that a single
 * transaction never modifies two aggregates, and so remote calls (Feign, PayOS) stay outside transactions.
 * REQUIRES_NEW also makes it safe to call from {@code AFTER_COMMIT} event listeners.
 */
@Component
public class TransactionRunner {
    private final TransactionTemplate template;

    public TransactionRunner(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
        this.template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T call(Supplier<T> work) {
        return template.execute(status -> work.get());
    }

    public void run(Runnable work) {
        template.executeWithoutResult(status -> work.run());
    }
}
