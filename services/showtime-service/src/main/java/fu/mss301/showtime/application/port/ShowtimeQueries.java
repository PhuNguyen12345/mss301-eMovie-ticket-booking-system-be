package fu.mss301.showtime.application.port;
import fu.mss301.showtime.application.dto.ShowtimePage;
import fu.mss301.showtime.application.dto.ShowtimeSearch;
public interface ShowtimeQueries {
    ShowtimePage search(ShowtimeSearch search);
}
