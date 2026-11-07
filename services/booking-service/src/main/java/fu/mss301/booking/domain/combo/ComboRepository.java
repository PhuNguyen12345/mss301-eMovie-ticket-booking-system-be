package fu.mss301.booking.domain.combo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ComboRepository {
    Combo save(Combo combo);

    Optional<Combo> findByIdForUpdate(Integer id);

    List<Combo> findAllByIds(Collection<Integer> ids);

    List<Combo> findAll();

    List<Combo> findByStatus(ComboStatus status);
}
