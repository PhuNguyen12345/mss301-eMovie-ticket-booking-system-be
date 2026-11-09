package fu.mss301.movie.infrastructure.persistence.adapter;

import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.movie.application.dto.*;
import fu.mss301.movie.application.exception.*;
import fu.mss301.movie.application.port.out.MovieCatalog;
import fu.mss301.movie.domain.model.Credit;
import fu.mss301.movie.domain.model.Movie;
import fu.mss301.movie.domain.model.MovieStatus;
import fu.mss301.movie.infrastructure.persistence.entity.MovieCredit;
import fu.mss301.movie.infrastructure.persistence.entity.MovieGenre;
import fu.mss301.movie.infrastructure.persistence.mapper.MovieMapper;
import fu.mss301.movie.infrastructure.persistence.repository.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
@Component
public class JpaMovieCatalog implements MovieCatalog {
    private final MovieRepository movies;
    private final GenreRepository genres;
    private final PersonRepository persons;
    private final MovieGenreRepository links;
    private final MovieCreditRepository credits;
    private final EntityManager entities;
    public JpaMovieCatalog(MovieRepository movies, GenreRepository genres, PersonRepository persons,
            MovieGenreRepository links, MovieCreditRepository credits, EntityManager entities) {
        this.movies = movies;
        this.genres = genres;
        this.persons = persons;
        this.links = links;
        this.credits = credits;
        this.entities = entities;
    }
    public PageResponseDTO<MovieResponseDTO> search(
            String keyword, MovieStatus status, Integer genreId, int page, int size) {
        var result = movies.searchMovies(keyword, status, genreId,
                                   PageRequest.of(page, size,
                                           Sort.by(Sort.Order.desc("releaseDate"), Sort.Order.desc("id"))))
                             .map(e -> MovieMapping.view(MovieMapper.domain(e, List.of(), List.of())));
        return new PageResponseDTO<>(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }
    public boolean exists(Integer id) {
        return movies.existsById(id);
    }
    public Movie find(Integer id, boolean lock) {
        var entity = (lock ? movies.findByIdForRelationUpdate(id) : movies.findById(id))
                             .orElseThrow(() -> new MovieNotFoundException(id));
        if (!lock)
            return MovieMapper.domain(entity, List.of(), List.of());
        return MovieMapper.domain(entity,
                links.findGenresByMovieId(id).stream().map(l -> l.getGenre().getId()).toList(),
                credits.findCreditsByMovieId(id)
                        .stream()
                        .map(c
                                -> new Credit(c.getId(), c.getPerson().getId(), c.getCreditType(),
                                        c.getCharacterName(), c.getDisplayOrder()))
                        .toList());
    }
    public Movie saveDetails(Movie value) {
        var entity = value.id() == null ? new fu.mss301.movie.infrastructure.persistence.entity.Movie()
                                        : movies.getReferenceById(value.id());
        MovieMapper.details(value, entity);
        if (value.id() == null)
            entity = movies.saveAndFlush(entity);
        else
            movies.flush();
        entities.refresh(entity);
        return MovieMapper.domain(entity, value.genreIds(), value.credits());
    }
    public void savePoster(Movie value) {
        var entity = movies.getReferenceById(value.id());
        entity.setPosterUrl(value.details().posterUrl());
        entity.setPosterPublicId(value.posterPublicId());
    }
    public void lockGenres(List<Integer> ids) {
        for (int id : ids) genres.findByIdForUpdate(id).orElseThrow(() -> new GenreNotFoundException(id));
    }
    public void lockPersons(List<Integer> ids) {
        for (int id : ids) persons.findByIdForUpdate(id).orElseThrow(() -> new PersonNotFoundException(id));
    }
    public void saveGenres(Movie movie) {
        var entity = movies.getReferenceById(movie.id());
        var existing = links.findGenresByMovieId(movie.id());
        Set<Integer> ids = existing.stream().map(l -> l.getGenre().getId()).collect(Collectors.toSet());
        links.deleteAll(
                existing.stream().filter(l -> !movie.genreIds().contains(l.getGenre().getId())).toList());
        links.saveAll(movie.genreIds()
                        .stream()
                        .filter(id -> !ids.contains(id))
                        .map(id -> new MovieGenre(entity, genres.getReferenceById(id)))
                        .toList());
        links.flush();
    }
    public void saveCredits(Movie movie) {
        var existing = credits.findCreditsByMovieId(movie.id());
        Map<Integer, MovieCredit> byId =
                existing.stream().collect(Collectors.toMap(MovieCredit::getId, c -> c));
        Set<Integer> retained =
                movie.credits().stream().map(Credit::id).filter(Objects::nonNull).collect(Collectors.toSet());
        List<MovieCredit> added = new ArrayList<>();
        for (Credit value : movie.credits()) {
            MovieCredit entity = value.id() == null ? null : byId.get(value.id());
            if (entity == null) {
                entity = new MovieCredit();
                entity.setMovie(movies.getReferenceById(movie.id()));
                entity.setPerson(persons.getReferenceById(value.personId()));
                entity.setCreditType(value.creditType());
                added.add(entity);
            }
            entity.setCharacterName(value.characterName());
            entity.setDisplayOrder(value.displayOrder());
        }
        credits.deleteAll(existing.stream().filter(c -> !retained.contains(c.getId())).toList());
        credits.saveAll(added);
        credits.flush();
    }
    public List<GenreResponseDTO> genres(Integer id) {
        return links.findGenresByMovieId(id)
                .stream()
                .map(l
                        -> new GenreResponseDTO(
                                l.getGenre().getId(), l.getGenre().getName(), l.getGenre().getDescription()))
                .toList();
    }
    public List<MovieCreditResponseDTO> credits(Integer id) {
        return credits.findCreditsByMovieId(id)
                .stream()
                .map(c
                        -> new MovieCreditResponseDTO(c.getId(), c.getPerson().getId(),
                                c.getPerson().getFullName(), c.getCreditType(), c.getCharacterName(),
                                c.getDisplayOrder()))
                .toList();
    }
}
