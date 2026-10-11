package fu.mss301.movie.repository;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import fu.mss301.movie.entity.Movie;

@Repository 
public interface MovieRepository extends JpaRepository<Movie, Integer> {
    
}
