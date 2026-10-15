package fu.mss301.movie.repository;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import fu.mss301.movie.entity.Genre;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

@Repository 
public interface GenreRepository extends JpaRepository<Genre, Integer> {
    Page<Genre> findByNameContainingIgnoreCase(String keyword, Pageable pageable);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Genre g WHERE g.id = :id")
    Optional<Genre> findByIdForUpdate(@Param("id") Integer id);
}
