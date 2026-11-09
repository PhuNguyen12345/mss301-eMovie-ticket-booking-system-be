package fu.mss301.movie.infrastructure.persistence.adapter;

import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.movie.application.dto.*;
import fu.mss301.movie.application.exception.*;
import fu.mss301.movie.application.port.out.ReferenceCatalog;
import fu.mss301.movie.domain.model.Genre;
import fu.mss301.movie.domain.model.Person;
import fu.mss301.movie.infrastructure.persistence.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
@Component
public class JpaReferenceCatalog implements ReferenceCatalog {
    private final GenreRepository genres;
    private final PersonRepository persons;
    private final MovieGenreRepository links;
    private final MovieCreditRepository credits;
    public JpaReferenceCatalog(GenreRepository genres, PersonRepository persons, MovieGenreRepository links,
            MovieCreditRepository credits) {
        this.genres = genres;
        this.persons = persons;
        this.links = links;
        this.credits = credits;
    }
    public PageResponseDTO<GenreResponseDTO> genres(String keyword, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("name", "id"));
        var result = (keyword == null ? genres.findAll(pageable)
                                      : genres.findByNameContainingIgnoreCase(keyword, pageable))
                             .map(g -> new GenreResponseDTO(g.getId(), g.getName(), g.getDescription()));
        return new PageResponseDTO<>(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }
    public Genre genre(Integer id, boolean lock) {
        var entity = (lock ? genres.findByIdForUpdate(id) : genres.findById(id))
                             .orElseThrow(() -> new GenreNotFoundException(id));
        return new Genre(entity.getId(), entity.getName(), entity.getDescription());
    }
    public boolean genreNameExists(String name, Integer excludingId) {
        return excludingId == null ? genres.existsByNameIgnoreCase(name)
                                   : genres.existsByNameIgnoreCaseAndIdNot(name, excludingId);
    }
    public boolean genreInUse(Integer id) {
        return links.existsByGenre_Id(id);
    }
    public Genre save(Genre value) {
        var entity = value.id() == null ? new fu.mss301.movie.infrastructure.persistence.entity.Genre()
                                        : genres.getReferenceById(value.id());
        entity.setName(value.name());
        entity.setDescription(value.description());
        if (value.id() == null)
            entity = genres.saveAndFlush(entity);
        else
            genres.flush();
        return new Genre(entity.getId(), entity.getName(), entity.getDescription());
    }
    public void deleteGenre(Integer id) {
        genres.delete(genres.getReferenceById(id));
        genres.flush();
    }
    public PageResponseDTO<PersonResponseDTO> persons(String keyword, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("fullName", "id"));
        var result = (keyword == null ? persons.findAll(pageable)
                                      : persons.findByFullNameContainingIgnoreCase(keyword, pageable))
                             .map(p -> new PersonResponseDTO(p.getId(), p.getFullName()));
        return new PageResponseDTO<>(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }
    public Person person(Integer id, boolean lock) {
        var entity = (lock ? persons.findByIdForUpdate(id) : persons.findById(id))
                             .orElseThrow(() -> new PersonNotFoundException(id));
        return new Person(entity.getId(), entity.getFullName());
    }
    public boolean personInUse(Integer id) {
        return credits.existsByPerson_Id(id);
    }
    public Person save(Person value) {
        var entity = value.id() == null ? new fu.mss301.movie.infrastructure.persistence.entity.Person()
                                        : persons.getReferenceById(value.id());
        entity.setFullName(value.fullName());
        if (value.id() == null)
            entity = persons.saveAndFlush(entity);
        else
            persons.flush();
        return new Person(entity.getId(), entity.getFullName());
    }
    public void deletePerson(Integer id) {
        persons.delete(persons.getReferenceById(id));
        persons.flush();
    }
}
