package fu.mss301.showtime.infrastructure.persistence;

import fu.mss301.showtime.domain.model.Showtime;
import fu.mss301.showtime.domain.repository.ShowtimeRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JpaShowtimeRepository implements ShowtimeRepository {
    private final ShowtimeJpaRepository showtimes;
    private final SeatPriceJpaRepository prices;

    public JpaShowtimeRepository(ShowtimeJpaRepository showtimes, SeatPriceJpaRepository prices) {
        this.showtimes = showtimes;
        this.prices = prices;
    }

    public Optional<Showtime> findById(Long id) {
        return showtimes.findById(id).map(this::toDomain);
    }

    public Optional<Showtime> findByIdForUpdate(Long id) {
        return showtimes.findByIdForUpdate(id).map(this::toDomain);
    }

    public Showtime save(Showtime showtime) {
        var entity = showtime.id() == null ? new ShowtimeEntity()
            : showtimes.findById(showtime.id()).orElseThrow();
        ShowtimeMapper.updateEntity(showtime, entity);
        entity = showtimes.saveAndFlush(entity);
        Long id = entity.getId();
        var existing = prices.findByShowtimeIdOrderBySeatTypeIdAsc(id);
        var byType = existing.stream().collect(Collectors.toMap(SeatPriceEntity::getSeatTypeId, Function.identity()));
        var types = showtime.seatPrices().stream().map(p -> p.seatTypeId()).collect(Collectors.toSet());
        prices.deleteAll(existing.stream().filter(p -> !types.contains(p.getSeatTypeId())).toList());
        for (var price : showtime.seatPrices()) {
            var row = byType.getOrDefault(price.seatTypeId(), new SeatPriceEntity());
            row.setShowtimeId(id);
            row.setSeatTypeId(price.seatTypeId());
            row.setPrice(price.price().amount());
            row.setCreatedAt(price.createdAt());
            row.setUpdatedAt(price.updatedAt());
            prices.save(row);
        }
        prices.flush();
        return toDomain(entity);
    }

    private Showtime toDomain(ShowtimeEntity entity) {
        return ShowtimeMapper.toDomain(entity, prices.findByShowtimeIdOrderBySeatTypeIdAsc(entity.getId()));
    }
}
