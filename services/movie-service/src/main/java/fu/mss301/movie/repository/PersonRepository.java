package fu.mss301.movie.repository;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import fu.mss301.movie.entity.Person;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository 
public interface PersonRepository extends JpaRepository<Person, Integer> {
    Page<Person> findByFullNameContainingIgnoreCase(String keyword, Pageable pageable);
}
