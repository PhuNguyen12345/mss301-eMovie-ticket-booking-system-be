package fu.mss301.booking.infrastructure.config;

import fu.mss301.booking.application.port.Transactions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * REQUIRES_NEW on purpose: policies run in AFTER_COMMIT listeners, where the finished transaction is still
 * bound to the thread. REQUIRED would join it and the work would never be committed.
 */
@Component
public class SpringTransactions implements Transactions {
    private final TransactionTemplate reads;
    private final TransactionTemplate writes;

    public SpringTransactions(PlatformTransactionManager manager) {
        reads = new TransactionTemplate(manager);
        reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        reads.setReadOnly(true);
        writes = new TransactionTemplate(manager);
        writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public <T> T read(Supplier<T> work) {
        return reads.execute(status -> work.get());
    }

    @Override
    public <T> T write(Supplier<T> work) {
        return writes.execute(status -> work.get());
    }
}
