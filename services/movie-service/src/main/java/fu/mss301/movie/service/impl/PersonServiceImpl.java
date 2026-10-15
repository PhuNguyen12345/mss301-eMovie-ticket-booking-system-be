package fu.mss301.movie.service.impl;

import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.movie.dto.PersonRequestDTO;
import fu.mss301.movie.dto.PersonResponseDTO;
import fu.mss301.movie.entity.Person;
import fu.mss301.movie.exception.PersonConflictException;
import fu.mss301.movie.exception.PersonNotFoundException;
import fu.mss301.movie.repository.MovieCreditRepository;
import fu.mss301.movie.repository.PersonRepository;
import fu.mss301.movie.service.PersonService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonServiceImpl implements PersonService {
    private final PersonRepository personRepository;
    private final MovieCreditRepository movieCreditRepository;

    @Override
    public PageResponseDTO<PersonResponseDTO> getPersons(String keyword, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be >= 0 and size must be between 1 and 100");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by("fullName", "id"));
        Page<Person> persons = keyword == null || keyword.isBlank()
            ? personRepository.findAll(pageable)
            : personRepository.findByFullNameContainingIgnoreCase(keyword.trim(), pageable);
        Page<PersonResponseDTO> result = persons.map(this::toResponse);
        return new PageResponseDTO<>(result.getContent(), result.getNumber(), result.getSize(),
            result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Override
    public PersonResponseDTO getPersonById(Integer id) {
        return toResponse(findPersonOrThrow(id));
    }

    @Override
    @Transactional
    public PersonResponseDTO createPerson(PersonRequestDTO request) {
        String fullName = validateRequest(request);
        Person person = new Person();
        person.setFullName(fullName);
        return toResponse(personRepository.saveAndFlush(person));
    }

    @Override
    @Transactional
    public PersonResponseDTO updatePerson(Integer id, PersonRequestDTO request) {
        String fullName = validateRequest(request);
        Person person = findPersonOrThrow(id);
        person.setFullName(fullName);
        personRepository.flush();
        return toResponse(person);
    }

    @Override
    @Transactional
    public void deletePerson(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Person ID must be positive");
        }
        Person person = personRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new PersonNotFoundException(id));
        if (movieCreditRepository.existsByPerson_Id(id)) {
            throw new PersonConflictException("Cannot delete a person who has movie credits");
        }
        personRepository.delete(person);
        personRepository.flush();
    }

    private Person findPersonOrThrow(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Person ID must be positive");
        }
        return personRepository.findById(id).orElseThrow(() -> new PersonNotFoundException(id));
    }

    private String validateRequest(PersonRequestDTO request) {
        if (request == null || request.getFullName() == null || request.getFullName().isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }
        String fullName = request.getFullName().trim();
        if (fullName.length() > 255) {
            throw new IllegalArgumentException("Full name must not exceed 255 characters");
        }
        return fullName;
    }

    private PersonResponseDTO toResponse(Person person) {
        return new PersonResponseDTO(person.getId(), person.getFullName());
    }
}
