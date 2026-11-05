package fu.mss301.showtime.application.port;
import java.util.function.Supplier;
public interface Transactions {
    <T> T read(Supplier<T> work);
    <T> T write(Supplier<T> work);
}
