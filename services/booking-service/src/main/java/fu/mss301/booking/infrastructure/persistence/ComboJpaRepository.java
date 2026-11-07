package fu.mss301.booking.infrastructure.persistence;

import fu.mss301.booking.domain.combo.ComboStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface ComboJpaRepository extends JpaRepository<ComboEntity, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ComboEntity c where c.id = :id")
    Optional<ComboEntity> lockById(@Param("id") Integer id);

    List<ComboEntity> findAllByOrderByIdAsc();

    List<ComboEntity> findByStatusOrderByIdAsc(ComboStatus status);
}
