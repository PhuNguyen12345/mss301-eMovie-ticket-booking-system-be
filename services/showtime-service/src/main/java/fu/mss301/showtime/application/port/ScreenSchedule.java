package fu.mss301.showtime.application.port;
import fu.mss301.showtime.domain.model.Schedule;
public interface ScreenSchedule {
    void lock(Integer screenId);
    boolean overlaps(Schedule schedule, Long excludedId);
}
