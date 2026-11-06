package fu.mss301.showtime.application.usecase;

import fu.mss301.showtime.application.dto.ShowtimePage;
import fu.mss301.showtime.application.dto.ShowtimeCommand;
import fu.mss301.showtime.application.dto.ShowtimeView;
import fu.mss301.showtime.domain.model.ShowtimeStatus;
import java.time.LocalDate;

public interface ShowtimeService {
    ShowtimePage getShowtimes(Integer movieId, Integer cinemaId,
        Integer screenId, LocalDate date, String location, ShowtimeStatus status, int page, int size);
    ShowtimeView getShowtimeById(Long id);
    ShowtimeView createShowtime(ShowtimeCommand request);
    ShowtimeView updateShowtime(Long id, ShowtimeCommand request);
    ShowtimeView cancelShowtime(Long id);
}
