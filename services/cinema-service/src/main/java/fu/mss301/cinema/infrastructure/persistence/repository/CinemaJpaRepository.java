package fu.mss301.cinema.infrastructure.persistence.repository;

import fu.mss301.cinema.infrastructure.persistence.entity.CinemaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CinemaJpaRepository extends JpaRepository<CinemaEntity, Integer> {}
