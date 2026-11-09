package fu.mss301.movie.infrastructure.persistence.repository;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import fu.mss301.movie.infrastructure.persistence.entity.MovieGenre;
import fu.mss301.movie.infrastructure.persistence.entity.MovieGenreId;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

@Repository 
public interface MovieGenreRepository extends JpaRepository<MovieGenre, MovieGenreId> {
    boolean existsByGenre_Id(Integer genreId);

    @Query("""
        SELECT mg FROM MovieGenre mg
        JOIN FETCH mg.genre
        WHERE mg.movie.id = :movieId
        ORDER BY mg.genre.name, mg.genre.id
        """)
    List<MovieGenre> findGenresByMovieId(@Param("movieId") Integer movieId);
}
