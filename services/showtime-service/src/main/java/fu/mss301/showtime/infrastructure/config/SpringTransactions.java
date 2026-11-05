package fu.mss301.showtime.infrastructure.config;

import fu.mss301.showtime.application.port.Transactions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.function.Supplier;

@Component
public class SpringTransactions implements Transactions {
    private final TransactionTemplate reads;
    private final TransactionTemplate writes;

    public SpringTransactions(PlatformTransactionManager manager) {
        reads = new TransactionTemplate(manager);
        reads.setReadOnly(true);
        writes = new TransactionTemplate(manager);
        writes.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public <T> T read(Supplier<T> work) { return reads.execute(status -> work.get()); }
    public <T> T write(Supplier<T> work) { return writes.execute(status -> work.get()); }
}
