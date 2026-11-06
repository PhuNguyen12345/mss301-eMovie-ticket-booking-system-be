package fu.mss301.showtime.infrastructure.persistence;

import fu.mss301.showtime.application.dto.*;
import fu.mss301.showtime.application.port.ShowtimeQueries;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import java.util.Locale;

@Repository
public class JpaShowtimeQueries implements ShowtimeQueries {
    private final ShowtimeJpaRepository repository;

    public JpaShowtimeQueries(ShowtimeJpaRepository repository) { this.repository = repository; }

    public ShowtimePage search(ShowtimeSearch search) {
        String location = null;
        if (search.location() != null && !search.location().isBlank()) {
            location = "%" + search.location().trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        }
        var page = repository.searchShowtimes(search.movieId(), search.cinemaId(), search.screenId(), search.status(),
            search.date() == null ? null : search.date().atStartOfDay(),
            search.date() == null ? null : search.date().plusDays(1).atStartOfDay(), location,
            PageRequest.of(search.page(), search.size(), Sort.by("startTime", "id")));
        var data = page.getContent().stream().map(e -> new ShowtimeView(e.getId(), e.getMovieId(), e.getCinemaId(),
            e.getScreenId(), e.getCinemaLocation(), e.getStartTime(), e.getEndTime(), e.getBasePrice(), e.getStatus(),
            e.getCreatedAt(), e.getUpdatedAt())).toList();
        return new ShowtimePage(data, page.getNumber(), page.getSize(), page.getTotalElements(),
            page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
