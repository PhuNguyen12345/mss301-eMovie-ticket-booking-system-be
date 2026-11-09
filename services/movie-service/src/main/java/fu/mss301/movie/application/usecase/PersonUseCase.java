package fu.mss301.movie.application.usecase;

import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.movie.application.dto.*;
import fu.mss301.movie.application.exception.*;
import fu.mss301.movie.application.port.in.PersonService;
import fu.mss301.movie.application.port.out.*;
import fu.mss301.movie.domain.model.Person;
public class PersonUseCase implements PersonService {
    private final ReferenceCatalog catalog;
    private final Transactions transactions;
    public PersonUseCase(ReferenceCatalog catalog, Transactions transactions) {
        this.catalog = catalog;
        this.transactions = transactions;
    }
    public PageResponseDTO<PersonResponseDTO> getPersons(String keyword, int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("Page must be >= 0 and size must be between 1 and 100");
        return transactions.read(
                ()
                        -> catalog.persons(
                                keyword == null || keyword.isBlank() ? null : keyword.trim(), page, size));
    }
    public PersonResponseDTO getPersonById(Integer id) {
        requireId(id);
        return transactions.read(() -> view(catalog.person(id, false)));
    }
    public PersonResponseDTO createPerson(PersonRequestDTO request) {
        return save(null, request);
    }
    public PersonResponseDTO updatePerson(Integer id, PersonRequestDTO request) {
        if (request == null)
            throw new IllegalArgumentException("Full name is required");
        Person value = Person.create(request.getFullName());
        requireId(id);
        return persist(id, value);
    }
    private PersonResponseDTO save(Integer id, PersonRequestDTO request) {
        if (request == null)
            throw new IllegalArgumentException("Full name is required");
        return persist(id, Person.create(request.getFullName()));
    }
    private PersonResponseDTO persist(Integer id, Person value) {
        return transactions.write(() -> {
            Person replacement = id == null ? value : catalog.person(id, false).rename(value.fullName());

            return view(catalog.save(replacement));
        });
    }
    public void deletePerson(Integer id) {
        requireId(id);
        transactions.write(() -> {
            catalog.person(id, true);
            if (catalog.personInUse(id))
                throw new PersonConflictException("Cannot delete a person who has movie credits");
            catalog.deletePerson(id);
            return null;
        });
    }
    private static PersonResponseDTO view(Person value) {
        return new PersonResponseDTO(value.id(), value.fullName());
    }
    private static void requireId(Integer id) {
        if (id == null || id <= 0)
            throw new IllegalArgumentException("Person ID must be positive");
    }
}
