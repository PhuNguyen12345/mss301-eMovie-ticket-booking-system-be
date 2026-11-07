package fu.mss301.cinema.infrastructure.persistence.repository;

import fu.mss301.cinema.infrastructure.persistence.entity.SeatEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatJpaRepository extends JpaRepository<SeatEntity, Integer> {
    List<SeatEntity> findByScreenIdOrderByRowCodeAscSeatNumberAsc(int screenId);
}
