package fu.mss301.showtime.repository;

import fu.mss301.showtime.entity.Showtime;
import fu.mss301.showtime.enums.ShowtimeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Optional;

public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {
    @Query("""
        SELECT s FROM Showtime s
        WHERE (:movieId IS NULL OR s.movieId = :movieId)
          AND (:cinemaId IS NULL OR s.cinemaId = :cinemaId)
          AND (:screenId IS NULL OR s.screenId = :screenId)
          AND (:status IS NULL OR s.status = :status)
          AND (:fromTime IS NULL OR s.startTime >= :fromTime)
          AND (:toTime IS NULL OR s.startTime < :toTime)
          AND (:location IS NULL OR LOWER(s.cinemaLocation) LIKE :location ESCAPE '!')
        """)
    Page<Showtime> searchShowtimes(
        @Param("movieId") Integer movieId, @Param("cinemaId") Integer cinemaId,
        @Param("screenId") Integer screenId, @Param("status") ShowtimeStatus status,
        @Param("fromTime") LocalDateTime fromTime, @Param("toTime") LocalDateTime toTime,
        @Param("location") String location, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Showtime s WHERE s.id = :id")
    Optional<Showtime> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        SELECT COUNT(s) FROM Showtime s
        WHERE s.screenId = :screenId AND s.status = :status
          AND s.startTime < :endTime AND s.endTime > :startTime
          AND (:excludedId IS NULL OR s.id <> :excludedId)
        """)
    long countOverlaps(@Param("screenId") Integer screenId,
        @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime,
        @Param("excludedId") Long excludedId, @Param("status") ShowtimeStatus status);
}
