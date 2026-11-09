package fu.mss301.movie.application.port.in;
import fu.mss301.common.dto.*;
import fu.mss301.movie.application.dto.MovieResponseDTO;
import fu.mss301.movie.application.dto.MovieRequestDTO;
import fu.mss301.movie.application.dto.MovieDetailResponseDTO;
import fu.mss301.movie.domain.model.MovieStatus;

public interface MovieService {
    PageResponseDTO<MovieResponseDTO> getMovies(
        String keyword, 
        MovieStatus status, 
        Integer genreId, 
        int page, 
        int size
    );
    MovieDetailResponseDTO getMovieById(Integer id);
    MovieResponseDTO createMovie(MovieRequestDTO request);
    MovieResponseDTO updateMovie(Integer id, MovieRequestDTO request); 
    void expireMovie(Integer id);
    MovieResponseDTO updateMovieStatus(
        Integer id,
        MovieStatus status
    );
}
