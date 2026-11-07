package fu.mss301.showtime.domain.repository;

import fu.mss301.showtime.domain.model.Showtime;
import java.util.Optional;

public interface ShowtimeRepository {
    Optional<Showtime> findById(Long id);
    Optional<Showtime> findByIdForUpdate(Long id);
    Showtime save(Showtime showtime);
}
