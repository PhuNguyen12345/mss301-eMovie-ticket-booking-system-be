package fu.mss301.showtime.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class ScreenScheduleLockRepository {
    private final EntityManager entityManager;

    public ScreenScheduleLockRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
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
