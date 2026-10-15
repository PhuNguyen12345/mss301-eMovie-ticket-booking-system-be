package fu.mss301.movie.service;

import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.movie.dto.PersonRequestDTO;
import fu.mss301.movie.dto.PersonResponseDTO;

public interface PersonService {
    PageResponseDTO<PersonResponseDTO> getPersons(String keyword, int page, int size);
    PersonResponseDTO getPersonById(Integer id);
    PersonResponseDTO createPerson(PersonRequestDTO request);
    PersonResponseDTO updatePerson(Integer id, PersonRequestDTO request);
    void deletePerson(Integer id);
}
