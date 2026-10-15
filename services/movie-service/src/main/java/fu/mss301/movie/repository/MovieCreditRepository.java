package fu.mss301.movie.repository;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import fu.mss301.movie.entity.MovieCredit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

@Repository 
public interface MovieCreditRepository extends JpaRepository<MovieCredit, Integer>  {
    boolean existsByPerson_Id(Integer personId);

    @Query("""
        SELECT mc FROM MovieCredit mc
        JOIN FETCH mc.person
        WHERE mc.movie.id = :movieId
        ORDER BY mc.creditType, mc.displayOrder, mc.id
        """)
    List<MovieCredit> findCreditsByMovieId(@Param("movieId") Integer movieId);
}
