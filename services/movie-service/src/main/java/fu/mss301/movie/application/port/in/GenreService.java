package fu.mss301.movie.application.port.in;

import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.movie.application.dto.GenreRequestDTO;
import fu.mss301.movie.application.dto.GenreResponseDTO;

public interface GenreService {
    PageResponseDTO<GenreResponseDTO> getGenres(String keyword, int page, int size);
    GenreResponseDTO getGenreById(Integer id);
    GenreResponseDTO createGenre(GenreRequestDTO request);
    GenreResponseDTO updateGenre(Integer id, GenreRequestDTO request);
    void deleteGenre(Integer id);
}
