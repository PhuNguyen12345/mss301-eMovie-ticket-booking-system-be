package fu.mss301.movie.service;

import org.springframework.stereotype.Service;
import fu.mss301.common.dto.*;
import fu.mss301.movie.dto.MovieResponseDTO;
import fu.mss301.movie.dto.MovieRequestDTO;
import fu.mss301.movie.enums.MovieStatus;

public interface MovieService {
    PageResponseDTO<MovieResponseDTO> getMovies(
        String keyword, 
        MovieStatus status, 
        Integer genreId, 
        int page, 
        int size
    );
    MovieResponseDTO getMovieById(Integer id);
    MovieResponseDTO createMovie(MovieRequestDTO request);
    MovieResponseDTO updateMovie(Integer id, MovieRequestDTO request); 
    void deleteMovie(Integer id);
    MovieResponseDTO updateMovieStatus(
        Integer id,
        MovieStatus status
    );
}
