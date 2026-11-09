package fu.mss301.movie;
import fu.mss301.movie.application.port.out.Transactions;
import java.util.function.Supplier;
public class TestTransactions implements Transactions {
    public <T> T read(Supplier<T> action) { return action.get(); }
    public <T> T write(Supplier<T> action) { return action.get(); }
}
