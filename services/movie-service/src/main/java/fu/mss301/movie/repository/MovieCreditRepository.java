package fu.mss301.movie.repository;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import fu.mss301.movie.entity.MovieCredit;

@Repository 
public interface MovieCreditRepository extends JpaRepository<MovieCredit, Integer>  {
    boolean existsByPerson_Id(Integer personId);
}
