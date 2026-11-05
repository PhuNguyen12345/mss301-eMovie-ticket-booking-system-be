package fu.mss301.showtime.infrastructure.persistence;

import fu.mss301.showtime.domain.model.*;
import java.util.List;

final class ShowtimeMapper {
    private ShowtimeMapper() {}

    static Showtime toDomain(ShowtimeEntity entity, List<SeatPriceEntity> prices) {
        return Showtime.restore(entity.getId(), new Schedule(entity.getMovieId(), entity.getCinemaId(),
            entity.getScreenId(), entity.getCinemaLocation(), entity.getStartTime(), entity.getEndTime(),
            new Price(entity.getBasePrice())), entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt(),
            prices.stream().map(p -> new SeatPrice(p.getId(), p.getSeatTypeId(), new Price(p.getPrice()),
                p.getCreatedAt(), p.getUpdatedAt())).toList());
    }

    static void updateEntity(Showtime source, ShowtimeEntity target) {
        var schedule = source.schedule();
        target.setMovieId(schedule.movieId());
        target.setCinemaId(schedule.cinemaId());
        target.setScreenId(schedule.screenId());
        target.setCinemaLocation(schedule.cinemaLocation());
        target.setStartTime(schedule.startTime());
        target.setEndTime(schedule.endTime());
        target.setBasePrice(schedule.basePrice().amount());
        target.setStatus(source.status());
        target.setCreatedAt(source.createdAt());
        target.setUpdatedAt(source.updatedAt());
    }
}
