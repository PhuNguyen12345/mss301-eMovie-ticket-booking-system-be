package fu.mss301.movie.infrastructure.config;

import fu.mss301.movie.application.exception.UncertainCommitException;
import fu.mss301.movie.application.port.out.Transactions;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.HeuristicCompletionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;
@Component
public class SpringTransactions implements Transactions {
    private final TransactionTemplate read;
    private final TransactionTemplate write;
    public SpringTransactions(PlatformTransactionManager manager) {
        read = new TransactionTemplate(manager);
        read.setReadOnly(true);
        write = new TransactionTemplate(manager);
    }
    public <T> T read(Supplier<T> action) {
        return read.execute(status -> action.get());
    }
    public <T> T write(Supplier<T> action) {
        try {
            return write.execute(status -> action.get());
        } catch (TransactionSystemException | HeuristicCompletionException exception) {
            throw new UncertainCommitException(exception);
        }
    }
}
