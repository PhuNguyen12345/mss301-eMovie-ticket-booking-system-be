package fu.mss301.movie.service;

import fu.mss301.movie.dto.GenreResponseDTO;
import fu.mss301.movie.dto.MovieCreditResponseDTO;
import fu.mss301.movie.dto.MovieCreditsRequestDTO;
import fu.mss301.movie.dto.MovieGenresRequestDTO;
import java.util.List;

public interface MovieRelationService {
    List<GenreResponseDTO> getGenres(Integer movieId);
    List<GenreResponseDTO> replaceGenres(Integer movieId, MovieGenresRequestDTO request);
    List<MovieCreditResponseDTO> getCredits(Integer movieId);
    List<MovieCreditResponseDTO> replaceCredits(Integer movieId, MovieCreditsRequestDTO request);
}
