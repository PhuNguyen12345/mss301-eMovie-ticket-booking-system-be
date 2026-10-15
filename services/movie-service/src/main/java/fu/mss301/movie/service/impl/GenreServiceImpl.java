package fu.mss301.movie.service.impl;

import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.movie.dto.GenreRequestDTO;
import fu.mss301.movie.dto.GenreResponseDTO;
import fu.mss301.movie.entity.Genre;
import fu.mss301.movie.exception.GenreConflictException;
import fu.mss301.movie.exception.GenreNotFoundException;
import fu.mss301.movie.repository.GenreRepository;
import fu.mss301.movie.repository.MovieGenreRepository;
import fu.mss301.movie.service.GenreService;
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
public class GenreServiceImpl implements GenreService {
    private final GenreRepository genreRepository;
    private final MovieGenreRepository movieGenreRepository;

    @Override
    public PageResponseDTO<GenreResponseDTO> getGenres(String keyword, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be >= 0 and size must be between 1 and 100");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by("name", "id"));
        Page<Genre> genres = keyword == null || keyword.isBlank()
            ? genreRepository.findAll(pageable)
            : genreRepository.findByNameContainingIgnoreCase(keyword.trim(), pageable);
        Page<GenreResponseDTO> result = genres.map(this::toResponse);
        return new PageResponseDTO<>(result.getContent(), result.getNumber(), result.getSize(),
            result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Override
    public GenreResponseDTO getGenreById(Integer id) {
        return toResponse(findGenreOrThrow(id));
    }

    @Override
    @Transactional
    public GenreResponseDTO createGenre(GenreRequestDTO request) {
        String name = validateRequest(request);
        if (genreRepository.existsByNameIgnoreCase(name)) {
            throw new GenreConflictException("Genre name already exists");
        }
        Genre genre = new Genre();
        genre.setName(name);
        genre.setDescription(request.getDescription());
        return toResponse(genreRepository.saveAndFlush(genre));
    }

    @Override
    @Transactional
    public GenreResponseDTO updateGenre(Integer id, GenreRequestDTO request) {
        String name = validateRequest(request);
        Genre genre = findGenreOrThrow(id);
        if (genreRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new GenreConflictException("Genre name already exists");
        }
        genre.setName(name);
        genre.setDescription(request.getDescription());
        genreRepository.flush();
        return toResponse(genre);
    }

    @Override
    @Transactional
    public void deleteGenre(Integer id) {
        Genre genre = findGenreOrThrow(id);
        if (movieGenreRepository.existsByGenre_Id(id)) {
            throw new GenreConflictException("Cannot delete a genre that is used by movies");
        }
        genreRepository.delete(genre);
        genreRepository.flush();
    }

    private Genre findGenreOrThrow(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Genre ID must be positive");
        }
        return genreRepository.findById(id).orElseThrow(() -> new GenreNotFoundException(id));
    }

    private String validateRequest(GenreRequestDTO request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Genre name is required");
        }
        String name = request.getName().trim();
        if (name.length() > 100) {
            throw new IllegalArgumentException("Genre name must not exceed 100 characters");
        }
        if (request.getDescription() != null && request.getDescription().length() > 500) {
            throw new IllegalArgumentException("Description must not exceed 500 characters");
        }
        return name;
    }

    private GenreResponseDTO toResponse(Genre genre) {
        return new GenreResponseDTO(genre.getId(), genre.getName(), genre.getDescription());
    }
}
