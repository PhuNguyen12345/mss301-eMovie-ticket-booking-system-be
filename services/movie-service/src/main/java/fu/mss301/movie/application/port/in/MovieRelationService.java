package fu.mss301.movie.application.port.in;

import fu.mss301.movie.application.dto.GenreResponseDTO;
import fu.mss301.movie.application.dto.MovieCreditResponseDTO;
import fu.mss301.movie.application.dto.MovieCreditsRequestDTO;
import fu.mss301.movie.application.dto.MovieGenresRequestDTO;
import java.util.List;

public interface MovieRelationService {
    List<GenreResponseDTO> getGenres(Integer movieId);
    List<GenreResponseDTO> replaceGenres(Integer movieId, MovieGenresRequestDTO request);
    List<MovieCreditResponseDTO> getCredits(Integer movieId);
    List<MovieCreditResponseDTO> replaceCredits(Integer movieId, MovieCreditsRequestDTO request);
}
