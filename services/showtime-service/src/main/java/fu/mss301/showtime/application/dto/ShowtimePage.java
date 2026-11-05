package fu.mss301.showtime.application.dto;
import java.util.List;
public record ShowtimePage(List<ShowtimeView> data, int page, int size, long totalElements,
                           int totalPages, boolean first, boolean last) {
    public ShowtimePage { data = List.copyOf(data); }
}
