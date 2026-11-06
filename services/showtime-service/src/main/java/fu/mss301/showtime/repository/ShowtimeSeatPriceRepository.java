package fu.mss301.showtime.repository;

import fu.mss301.showtime.entity.ShowtimeSeatPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShowtimeSeatPriceRepository extends JpaRepository<ShowtimeSeatPrice, Integer> {
    List<ShowtimeSeatPrice> findByShowtimeIdOrderBySeatTypeIdAsc(Long showtimeId);
}
