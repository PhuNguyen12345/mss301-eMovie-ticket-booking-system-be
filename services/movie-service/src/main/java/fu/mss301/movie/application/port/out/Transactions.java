package fu.mss301.movie.application.port.out;

import java.util.function.Supplier;
public interface Transactions {
    <T> T read(Supplier<T> action);
    <T> T write(Supplier<T> action);
}
