package fu.mss301.booking.infrastructure.persistence;

import fu.mss301.booking.domain.combo.Combo;
import fu.mss301.booking.domain.combo.ComboRepository;
import fu.mss301.booking.domain.combo.ComboStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JpaComboRepository implements ComboRepository {
    private final ComboJpaRepository jpa;

    JpaComboRepository(ComboJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Combo save(Combo combo) {
        var entity = combo.id() == null ? new ComboEntity() : jpa.findById(combo.id()).orElseThrow();
        entity.setName(combo.name());
        entity.setDescription(combo.description());
        entity.setPrice(combo.price());
        entity.setStatus(combo.status());
        entity.setCreatedAt(combo.createdAt());
        entity.setUpdatedAt(combo.updatedAt());
        return toDomain(jpa.saveAndFlush(entity));
    }

    @Override
    public Optional<Combo> findByIdForUpdate(Integer id) {
        return jpa.lockById(id).map(JpaComboRepository::toDomain);
    }

    @Override
    public List<Combo> findAllByIds(Collection<Integer> ids) {
        return jpa.findAllById(ids).stream().map(JpaComboRepository::toDomain).toList();
    }

    @Override
    public List<Combo> findAll() {
        return jpa.findAllByOrderByIdAsc().stream().map(JpaComboRepository::toDomain).toList();
    }

    @Override
    public List<Combo> findByStatus(ComboStatus status) {
        return jpa.findByStatusOrderByIdAsc(status).stream().map(JpaComboRepository::toDomain).toList();
    }

    private static Combo toDomain(ComboEntity entity) {
        return Combo.restore(entity.getId(), entity.getName(), entity.getDescription(), entity.getPrice(),
            entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
