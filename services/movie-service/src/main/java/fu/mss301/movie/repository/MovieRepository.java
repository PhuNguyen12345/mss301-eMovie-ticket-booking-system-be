package fu.mss301.movie.repository;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import fu.mss301.movie.entity.Movie;
import fu.mss301.movie.enums.MovieStatus;
import jakarta.persistence.LockModeType;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
@Repository 
public interface MovieRepository extends JpaRepository<Movie, Integer> {
    
    @Query (
        value = """
                SELECT m 
                from Movie m 
                WHERE (:keyword IS NULL 
                    OR LOWER(m.title) LIKE 
                        LOWER(CONCAT('%', :keyword, '%')))
                AND (:status IS NULL
                    OR m.status = :status)
                AND (:genreId IS NULL OR EXISTS (
                    SELECT mg 
                    FROM MovieGenre mg 
                    WHERE mg.movie.id = m.id 
                    AND mg.genre.id = :genreId))
                """,
        countQuery = """
                SELECT COUNT(m)
                from Movie m
                WHERE (:keyword IS NULL
                    OR LOWER(m.title) LIKE 
                        LOWER(CONCAT('%', :keyword, '%')))
                AND (:status IS NULL
                    OR m.status = :status)
                AND (:genreId IS NULL OR EXISTS (
                    SELECT mg
                    FROM MovieGenre mg 
                    WHERE mg.movie.id = m.id
                    AND mg.genre.id = :genreId))
                """
    )
    Page<Movie> searchMovies (
        @Param("keyword") String keyword, 
        @Param("status") MovieStatus status, 
        @Param("genreId") Integer genreId, 
        Pageable pageable 
    ); 

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Movie m WHERE m.id = :id")
    Optional<Movie> findByIdForPosterUpdate(@Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Movie m WHERE m.id = :id")
    Optional<Movie> findByIdForRelationUpdate(@Param("id") Integer id);
}
