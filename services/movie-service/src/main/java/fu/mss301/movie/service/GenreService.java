package fu.mss301.movie.service;

import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.movie.dto.GenreRequestDTO;
import fu.mss301.movie.dto.GenreResponseDTO;

public interface GenreService {
    PageResponseDTO<GenreResponseDTO> getGenres(String keyword, int page, int size);
    GenreResponseDTO getGenreById(Integer id);
    GenreResponseDTO createGenre(GenreRequestDTO request);
    GenreResponseDTO updateGenre(Integer id, GenreRequestDTO request);
    void deleteGenre(Integer id);
}
