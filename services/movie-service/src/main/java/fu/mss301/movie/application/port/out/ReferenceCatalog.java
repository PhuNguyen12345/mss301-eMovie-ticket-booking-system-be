package fu.mss301.movie.application.port.out;

import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.movie.application.dto.*;
import fu.mss301.movie.domain.model.Genre;
import fu.mss301.movie.domain.model.Person;
public interface ReferenceCatalog {
    PageResponseDTO<GenreResponseDTO> genres(String keyword, int page, int size);
    Genre genre(Integer id, boolean lock);
    boolean genreNameExists(String name, Integer excludingId);
    boolean genreInUse(Integer id);
    Genre save(Genre genre);
    void deleteGenre(Integer id);
    PageResponseDTO<PersonResponseDTO> persons(String keyword, int page, int size);
    Person person(Integer id, boolean lock);
    boolean personInUse(Integer id);
    Person save(Person person);
    void deletePerson(Integer id);
}
