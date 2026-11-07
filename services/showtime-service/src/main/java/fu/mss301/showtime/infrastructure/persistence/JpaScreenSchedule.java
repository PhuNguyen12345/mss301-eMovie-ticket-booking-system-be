package fu.mss301.showtime.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import fu.mss301.showtime.application.port.ScreenSchedule;
import fu.mss301.showtime.domain.model.Schedule;
import fu.mss301.showtime.domain.model.ShowtimeStatus;
import org.springframework.stereotype.Repository;

@Repository
public class JpaScreenSchedule implements ScreenSchedule {
    private final EntityManager entityManager;
    private final ShowtimeJpaRepository showtimes;

    public JpaScreenSchedule(EntityManager entityManager, ShowtimeJpaRepository showtimes) {
        this.entityManager = entityManager;
        this.showtimes = showtimes;
    }

    public boolean overlaps(Schedule schedule, Long excludedId) {
        return showtimes.countOverlaps(schedule.screenId(), schedule.startTime(), schedule.endTime(),
            excludedId, ShowtimeStatus.SCHEDULED) > 0;
    }

    public void lock(Integer screenId) {
        entityManager.createNativeQuery("""
            INSERT INTO screen_schedule_lock (screen_id) VALUES (:screenId)
            ON DUPLICATE KEY UPDATE screen_id = :screenId
            """)
            .setParameter("screenId", screenId)
            .executeUpdate();
    }
}
