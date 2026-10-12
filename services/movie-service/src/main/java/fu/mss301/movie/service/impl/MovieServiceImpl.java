package fu.mss301.movie.service.impl;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;

import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.movie.dto.MovieRequestDTO;
import fu.mss301.movie.dto.MovieResponseDTO;
import fu.mss301.movie.entity.Movie;
import fu.mss301.movie.enums.MovieStatus;
import fu.mss301.movie.repository.MovieRepository;
import fu.mss301.movie.service.MovieService;
import jakarta.persistence.EntityManager;
import fu.mss301.movie.exception.*;

@Service 
@Transactional (readOnly = true)
public class MovieServiceImpl implements MovieService {

    private final MovieRepository movieRepository; 
    private final EntityManager entityManager; 

    public MovieServiceImpl (MovieRepository movieRepository, EntityManager entityManager) {
        this.movieRepository = movieRepository; 
        this.entityManager = entityManager; 
    }

    @Override
    public PageResponseDTO<MovieResponseDTO> getMovies(
        String keyword, 
        MovieStatus status, 
        Integer genreId, 
        int page,
        int size
    ) {
        //format keyword before searching 
        String normalizedKeyword = 
            keyword == null || keyword.isBlank() 
            ? null 
            : keyword.trim(); 


        //define pageable with page, size and sort direction 
        Pageable pageable = PageRequest.of(
            page, 
            size, 
            Sort.by(
                Sort.Order.desc("releaseDate"),
                Sort.Order.desc("id")
            )
        ); 

        //output result 
        Page<MovieResponseDTO> result = 
            movieRepository.searchMovies(
                normalizedKeyword, 
                status, 
                genreId, 
                pageable
            ).map(this::toResponseDTO); 
        
        return new PageResponseDTO<>(
            result.getContent(), 
            result.getNumber(), 
            result.getSize(), 
            result.getTotalElements(),
            result.getTotalPages(), 
            result.isFirst(),
            result.isLast()
        ); 
    }

    @Override
    public MovieResponseDTO getMovieById(Integer id) {
        return toResponseDTO(findMovieOrThrow(id)); 
    }

    @Override
    @Transactional 
    public MovieResponseDTO createMovie(MovieRequestDTO request) {  
        validateRequest(request);
        Movie movie = new Movie();
        applyRequest(movie, request);
        //save and refresh changes 
        Movie savedMovie = movieRepository.saveAndFlush(movie); 

        //re-read timestamps 
        entityManager.refresh(savedMovie);
        return toResponseDTO(savedMovie); 
    }

    @Override
    @Transactional 
    public MovieResponseDTO updateMovie(Integer id, MovieRequestDTO request) {
        validateRequest(request);
        Movie movie = findMovieOrThrow(id); 
        applyRequest(movie, request); 

        //entity managed by transaction 
        //hibernate check changes using dirty checking
        movieRepository.flush(); 
        entityManager.refresh(movie); 

        return toResponseDTO(movie); 
    }

    @Override
    public void deleteMovie(Integer id) {
        updateMovieStatus(id, MovieStatus.EXPIRED); 
    }

    private MovieResponseDTO toResponseDTO(Movie movie) {
        return new MovieResponseDTO(
            movie.getId(),
            movie.getTitle(),
            movie.getDescription(),
            movie.getDurationMinutes(),
            movie.getReleaseDate(),
            movie.getLanguage(),
            movie.getAgeRating(),
            movie.getPosterUrl(),
            movie.getTrailerUrl(),
            movie.getStatus(),
            movie.getCreatedAt(),
            movie.getUpdatedAt()
        );
    }

    private Movie findMovieOrThrow(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                "Movie ID must be positive"
            );
        }

        return movieRepository.findById(id)
            .orElseThrow(() -> new MovieNotFoundException(id));
    }

    private void validateRequest(MovieRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException(
                "Movie request is required"
            );
        }

        if (request.getTitle() == null
                || request.getTitle().isBlank()
                || request.getTitle().trim().length() > 255) {
            throw new IllegalArgumentException(
                "Title is required and must not exceed 255 characters"
            );
        }

        if (request.getDurationMinutes() == null
                || request.getDurationMinutes() <= 0) {
            throw new IllegalArgumentException(
                "Duration must be greater than 0"
            );
        }

        if (request.getReleaseDate() == null) {
            throw new IllegalArgumentException(
                "Release date is required"
            );
        }

        if (request.getStatus() == null) {
            throw new IllegalArgumentException(
                "Movie status is required"
            );
        }

        validateLength(request.getLanguage(), 50, "Language");
        validateLength(request.getAgeRating(), 20, "Age rating");
        validateLength(request.getPosterUrl(), 500, "Poster URL");
        validateLength(request.getTrailerUrl(), 500, "Trailer URL");
    }

    private void validateLength(
        String value,
        int maxLength,
        String fieldName
    ) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(
                fieldName + " must not exceed "
                    + maxLength + " characters"
            );
        }
    }

    private void applyRequest(
        Movie movie,
        MovieRequestDTO request
    ) {
        movie.setTitle(request.getTitle().trim());
        movie.setDescription(request.getDescription());
        movie.setDurationMinutes(request.getDurationMinutes());
        movie.setReleaseDate(request.getReleaseDate());
        movie.setLanguage(request.getLanguage());
        movie.setAgeRating(request.getAgeRating());
        movie.setPosterUrl(request.getPosterUrl());
        movie.setTrailerUrl(request.getTrailerUrl());
        movie.setStatus(request.getStatus());
    }

    @Override
    @Transactional 
    public MovieResponseDTO updateMovieStatus(Integer id, MovieStatus status) {
        //check status 
        if (status == null) {
            throw new IllegalArgumentException(
                "Movie status is required"
            ); 
        }

        Movie movie = findMovieOrThrow(id); 
        movie.setStatus(status); 

        movieRepository.flush();
        entityManager.refresh(movie); 

        return toResponseDTO(movie); 
    }
    
}

