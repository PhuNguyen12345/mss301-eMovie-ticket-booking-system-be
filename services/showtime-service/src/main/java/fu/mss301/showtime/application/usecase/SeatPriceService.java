package fu.mss301.showtime.application.usecase;

import fu.mss301.showtime.application.dto.SeatPriceCommand;
import fu.mss301.showtime.application.dto.SeatPriceView;
import fu.mss301.showtime.application.exception.ShowtimeNotFoundException;
import fu.mss301.showtime.application.port.Transactions;
import fu.mss301.showtime.domain.model.*;
import fu.mss301.showtime.domain.repository.ShowtimeRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

public class SeatPriceService {
    private final ShowtimeRepository showtimes;
    private final Transactions transactions;
    private final Clock clock;

    public SeatPriceService(ShowtimeRepository showtimes, Transactions transactions, Clock clock) {
        this.showtimes = showtimes;
        this.transactions = transactions;
        this.clock = clock;
    }

    public List<SeatPriceView> getSeatPrices(Long id) {
        Ids.requirePositive(id, "Showtime");
        return transactions.read(() -> views(showtimes.findById(id)
            .orElseThrow(() -> new ShowtimeNotFoundException(id))));
    }

    public List<SeatPriceView> replaceSeatPrices(Long id, List<SeatPriceCommand> commands) {
        Ids.requirePositive(id, "Showtime");
        if (commands == null) throw new IllegalArgumentException("Seat prices are required");
        var selections = commands.stream().map(command -> {
            if (command == null) throw new IllegalArgumentException("Seat price is required");
            return new SeatPriceSelection(command.seatTypeId(), new Price(command.price()));
        }).toList();
        return transactions.write(() -> {
            var showtime = showtimes.findByIdForUpdate(id).orElseThrow(() -> new ShowtimeNotFoundException(id));
            showtime.replaceSeatPrices(selections, LocalDateTime.now(clock));
            return views(showtimes.save(showtime));
        });
    }

    private List<SeatPriceView> views(Showtime showtime) {
        return showtime.seatPrices().stream().map(price ->
            new SeatPriceView(price.id(), price.seatTypeId(), price.price().amount())).toList();
    }
}
