package fu.mss301.movie.application.port.in;

import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.movie.application.dto.PersonRequestDTO;
import fu.mss301.movie.application.dto.PersonResponseDTO;

public interface PersonService {
    PageResponseDTO<PersonResponseDTO> getPersons(String keyword, int page, int size);
    PersonResponseDTO getPersonById(Integer id);
    PersonResponseDTO createPerson(PersonRequestDTO request);
    PersonResponseDTO updatePerson(Integer id, PersonRequestDTO request);
    void deletePerson(Integer id);
}
