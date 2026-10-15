package fu.mss301.movie.repository;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import fu.mss301.movie.entity.Person;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

@Repository 
public interface PersonRepository extends JpaRepository<Person, Integer> {
    Page<Person> findByFullNameContainingIgnoreCase(String keyword, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Person p WHERE p.id = :id")
    Optional<Person> findByIdForUpdate(@Param("id") Integer id);
}
