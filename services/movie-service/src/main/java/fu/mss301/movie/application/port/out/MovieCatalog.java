package fu.mss301.movie.application.port.out;

import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.movie.application.dto.*;
import fu.mss301.movie.domain.model.*;
import java.util.List;
public interface MovieCatalog {
    PageResponseDTO<MovieResponseDTO> search(
            String keyword, MovieStatus status, Integer genreId, int page, int size);
    boolean exists(Integer id);
    Movie find(Integer id, boolean lock);
    Movie saveDetails(Movie movie);
    void savePoster(Movie movie);
    void lockGenres(List<Integer> ids);
    void lockPersons(List<Integer> ids);
    void saveGenres(Movie movie);
    void saveCredits(Movie movie);
    List<GenreResponseDTO> genres(Integer movieId);
    List<MovieCreditResponseDTO> credits(Integer movieId);
}
