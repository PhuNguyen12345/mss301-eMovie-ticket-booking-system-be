package fu.mss301.showtime.infrastructure.persistence;

import fu.mss301.showtime.infrastructure.persistence.SeatPriceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatPriceJpaRepository extends JpaRepository<SeatPriceEntity, Integer> {
    List<SeatPriceEntity> findByShowtimeIdOrderBySeatTypeIdAsc(Long showtimeId);
}
