package fu.mss301.showtime.service;

import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.showtime.dto.ShowtimeRequestDTO;
import fu.mss301.showtime.dto.ShowtimeResponseDTO;
import fu.mss301.showtime.enums.ShowtimeStatus;
import java.time.LocalDate;

public interface ShowtimeService {
    PageResponseDTO<ShowtimeResponseDTO> getShowtimes(Integer movieId, Integer cinemaId,
        Integer screenId, LocalDate date, String location, ShowtimeStatus status, int page, int size);
    ShowtimeResponseDTO getShowtimeById(Long id);
    ShowtimeResponseDTO createShowtime(ShowtimeRequestDTO request);
    ShowtimeResponseDTO updateShowtime(Long id, ShowtimeRequestDTO request);
    ShowtimeResponseDTO cancelShowtime(Long id);
}
