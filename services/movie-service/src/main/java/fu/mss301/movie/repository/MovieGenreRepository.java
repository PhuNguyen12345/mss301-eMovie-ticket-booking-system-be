package fu.mss301.movie.repository;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import fu.mss301.movie.entity.MovieGenre;
import fu.mss301.movie.entity.MovieGenreId;

@Repository 
public interface MovieGenreRepository extends JpaRepository<MovieGenre, MovieGenreId> {
    boolean existsByGenre_Id(Integer genreId);
}
