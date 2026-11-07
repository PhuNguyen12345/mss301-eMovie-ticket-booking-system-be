package fu.mss301.booking.application.combo;

import fu.mss301.booking.application.exception.ComboNotFoundException;
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.domain.combo.Combo;
import fu.mss301.booking.domain.combo.ComboRepository;
import fu.mss301.booking.domain.combo.ComboStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/** The combo menu: customers read what is on sale, admins manage it. */
public class ComboService {
    private final ComboRepository combos;
    private final Transactions transactions;
    private final Clock clock;

    public ComboService(ComboRepository combos, Transactions transactions, Clock clock) {
        this.combos = combos;
        this.transactions = transactions;
        this.clock = clock;
    }

    public List<ComboView> listOnSale() {
        return transactions.read(() -> combos.findByStatus(ComboStatus.ACTIVE).stream().map(ComboView::of).toList());
    }

    public List<ComboView> listAll() {
        return transactions.read(() -> combos.findAll().stream().map(ComboView::of).toList());
    }

    public ComboView create(ComboCommand command) {
        return transactions.write(() -> ComboView.of(combos.save(
            Combo.create(command.name(), command.description(), command.price(), LocalDateTime.now(clock)))));
    }

    public ComboView update(Integer id, ComboCommand command) {
        return change(id, combo -> combo.update(command.name(), command.description(), command.price(),
            LocalDateTime.now(clock)));
    }

    public ComboView activate(Integer id) {
        return change(id, combo -> combo.activate(LocalDateTime.now(clock)));
    }

    public ComboView deactivate(Integer id) {
        return change(id, combo -> combo.deactivate(LocalDateTime.now(clock)));
    }

    private ComboView change(Integer id, Consumer<Combo> change) {
        return transactions.write(() -> {
            var combo = combos.findByIdForUpdate(id).orElseThrow(ComboNotFoundException::new);
            change.accept(combo);
            return ComboView.of(combos.save(combo));
        });
    }
}
