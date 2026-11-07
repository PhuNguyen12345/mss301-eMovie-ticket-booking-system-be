package fu.mss301.booking.application.port;

import java.util.function.Supplier;

/**
 * Runs one unit of work in its own new transaction. Use cases call it once per aggregate so a single
 * transaction never modifies two aggregates and remote calls (Feign, PayOS) stay outside transactions.
 * Always starting a new transaction also makes it safe to call after another transaction has committed.
 */
public interface Transactions {
    <T> T read(Supplier<T> work);

    <T> T write(Supplier<T> work);

    default void run(Runnable work) {
        write(() -> {
            work.run();
            return null;
        });
    }
}
