package fu.mss301.cinema.infrastructure.persistence.repository;

import fu.mss301.cinema.infrastructure.persistence.entity.ScreenEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreenJpaRepository extends JpaRepository<ScreenEntity, Integer> {
    List<ScreenEntity> findByCinemaIdOrderById(int cinemaId);
}
