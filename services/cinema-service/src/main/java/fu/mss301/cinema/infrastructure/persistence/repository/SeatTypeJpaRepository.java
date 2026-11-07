package fu.mss301.cinema.infrastructure.persistence.repository;

import fu.mss301.cinema.infrastructure.persistence.entity.SeatTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatTypeJpaRepository extends JpaRepository<SeatTypeEntity, Integer> {}
