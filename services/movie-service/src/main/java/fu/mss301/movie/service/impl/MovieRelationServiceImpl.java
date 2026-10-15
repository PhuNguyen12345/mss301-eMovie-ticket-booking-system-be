package fu.mss301.movie.service.impl;

import fu.mss301.movie.dto.*;
import fu.mss301.movie.entity.*;
import fu.mss301.movie.enums.CreditType;
import fu.mss301.movie.exception.*;
import fu.mss301.movie.repository.*;
import fu.mss301.movie.service.MovieRelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieRelationServiceImpl implements MovieRelationService {
    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final PersonRepository personRepository;
    private final MovieGenreRepository movieGenreRepository;
    private final MovieCreditRepository movieCreditRepository;

    @Override
    public List<GenreResponseDTO> getGenres(Integer movieId) {
        requireMovie(movieId);
        return readGenres(movieId);
    }

    @Override
    @Transactional
    public List<GenreResponseDTO> replaceGenres(Integer movieId, MovieGenresRequestDTO request) {
        if (request == null || request.getGenreIds() == null) {
            throw new IllegalArgumentException("genreIds is required; use [] to clear genres");
        }
        Set<Integer> requestedIds = new TreeSet<>();
        for (Integer id : request.getGenreIds()) {
            requirePositiveId(id, "Genre");
            if (!requestedIds.add(id)) {
                throw new IllegalArgumentException("Duplicate genre ID: " + id);
            }
        }

        Movie movie = lockMovie(movieId);
        Map<Integer, Genre> genres = new HashMap<>();
        // Acquire reference locks in a consistent order; deletion uses the same locks.
        for (Integer id : requestedIds) {
            genres.put(id, genreRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new GenreNotFoundException(id)));
        }

        List<MovieGenre> existing = movieGenreRepository.findGenresByMovieId(movieId);
        Set<Integer> existingIds = existing.stream().map(link -> link.getGenre().getId())
            .collect(Collectors.toSet());
        List<MovieGenre> removed = existing.stream()
            .filter(link -> !requestedIds.contains(link.getGenre().getId())).toList();
        List<MovieGenre> added = requestedIds.stream().filter(id -> !existingIds.contains(id))
            .map(id -> new MovieGenre(movie, genres.get(id))).toList();

        // Preserve existing composite keys instead of deleting and reinserting them.
        movieGenreRepository.deleteAll(removed);
        movieGenreRepository.saveAll(added);
        movieGenreRepository.flush();
        return readGenres(movieId);
    }

    @Override
    public List<MovieCreditResponseDTO> getCredits(Integer movieId) {
        requireMovie(movieId);
        return readCredits(movieId);
    }

    @Override
    @Transactional
    public List<MovieCreditResponseDTO> replaceCredits(Integer movieId, MovieCreditsRequestDTO request) {
        if (request == null || request.getCredits() == null) {
            throw new IllegalArgumentException("credits is required; use [] to clear credits");
        }
        Set<CreditKey> requestedKeys = new HashSet<>();
        Set<Integer> personIds = new TreeSet<>();
        for (MovieCreditRequestDTO item : request.getCredits()) {
            validateCredit(item);
            if (!requestedKeys.add(new CreditKey(item.getPersonId(), item.getCreditType()))) {
                throw new IllegalArgumentException("Duplicate person and credit type");
            }
            personIds.add(item.getPersonId());
        }

        Movie movie = lockMovie(movieId);
        Map<Integer, Person> persons = new HashMap<>();
        for (Integer id : personIds) {
            persons.put(id, personRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new PersonNotFoundException(id)));
        }

        List<MovieCredit> existing = movieCreditRepository.findCreditsByMovieId(movieId);
        Map<CreditKey, MovieCredit> existingByKey = new HashMap<>();
        for (MovieCredit credit : existing) {
            if (existingByKey.put(keyOf(credit), credit) != null) {
                throw new MovieRelationConflictException(
                    "Existing credits contain duplicate person and credit type; resolve duplicates first"
                );
            }
        }
        List<MovieCredit> removed = existing.stream()
            .filter(credit -> !requestedKeys.contains(keyOf(credit))).toList();
        List<MovieCredit> added = new ArrayList<>();

        // All request validation and reference checks finish before changing any entity.
        for (MovieCreditRequestDTO item : request.getCredits()) {
            MovieCredit credit = existingByKey.get(new CreditKey(item.getPersonId(), item.getCreditType()));
            if (credit == null) {
                credit = new MovieCredit();
                credit.setMovie(movie);
                credit.setPerson(persons.get(item.getPersonId()));
                credit.setCreditType(item.getCreditType());
                added.add(credit);
            }
            credit.setCharacterName(normalizeCharacterName(item.getCharacterName()));
            credit.setDisplayOrder(item.getDisplayOrder());
        }

        movieCreditRepository.deleteAll(removed);
        movieCreditRepository.saveAll(added);
        // Managed credits retain their IDs and are updated by dirty checking.
        movieCreditRepository.flush();
        return readCredits(movieId);
    }

    private List<GenreResponseDTO> readGenres(Integer movieId) {
        return movieGenreRepository.findGenresByMovieId(movieId).stream()
            .map(link -> new GenreResponseDTO(link.getGenre().getId(), link.getGenre().getName(),
                link.getGenre().getDescription())).toList();
    }

    private List<MovieCreditResponseDTO> readCredits(Integer movieId) {
        return movieCreditRepository.findCreditsByMovieId(movieId).stream()
            .map(credit -> new MovieCreditResponseDTO(credit.getId(), credit.getPerson().getId(),
                credit.getPerson().getFullName(), credit.getCreditType(), credit.getCharacterName(),
                credit.getDisplayOrder())).toList();
    }

    private void requireMovie(Integer movieId) {
        requirePositiveId(movieId, "Movie");
        if (!movieRepository.existsById(movieId)) {
            throw new MovieNotFoundException(movieId);
        }
    }

    private Movie lockMovie(Integer movieId) {
        requirePositiveId(movieId, "Movie");
        return movieRepository.findByIdForRelationUpdate(movieId)
            .orElseThrow(() -> new MovieNotFoundException(movieId));
    }

    private void requirePositiveId(Integer id, String entityName) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(entityName + " ID must be positive");
        }
    }

    private void validateCredit(MovieCreditRequestDTO item) {
        if (item == null) {
            throw new IllegalArgumentException("Credit item must not be null");
        }
        requirePositiveId(item.getPersonId(), "Person");
        if (item.getCreditType() == null) {
            throw new IllegalArgumentException("Credit type is required");
        }
        if (item.getDisplayOrder() == null || item.getDisplayOrder() < 1) {
            throw new IllegalArgumentException("Display order must be >= 1");
        }
        String characterName = normalizeCharacterName(item.getCharacterName());
        if (characterName != null && characterName.length() > 255) {
            throw new IllegalArgumentException("Character name must not exceed 255 characters");
        }
        if (item.getCreditType() == CreditType.DIRECTOR && characterName != null) {
            throw new IllegalArgumentException("Director credit must not have a character name");
        }
    }

    private String normalizeCharacterName(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private CreditKey keyOf(MovieCredit credit) {
        return new CreditKey(credit.getPerson().getId(), credit.getCreditType());
    }

    private record CreditKey(Integer personId, CreditType creditType) {}
}
