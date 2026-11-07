package fu.mss301.user.infrastructure.persistence.adapter;

import fu.mss301.user.domain.repository.PersistenceConflictException;
import java.util.function.Supplier;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;

final class PersistenceAdapterSupport {
    private PersistenceAdapterSupport() { }

    static <T> T call(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataIntegrityViolationException | ConcurrencyFailureException exception) {
            throw new PersistenceConflictException(exception);
        }
    }
}
