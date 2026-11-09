package fu.mss301.movie.application.usecase;

import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.movie.application.dto.*;
import fu.mss301.movie.application.port.in.MovieService;
import fu.mss301.movie.application.port.out.*;
import fu.mss301.movie.domain.model.*;
public class MovieUseCase implements MovieService {
    private final MovieCatalog catalog;
    private final Transactions transactions;
    public MovieUseCase(MovieCatalog catalog, Transactions transactions) {
        this.catalog = catalog;
        this.transactions = transactions;
    }
    public PageResponseDTO<MovieResponseDTO> getMovies(
            String keyword, MovieStatus status, Integer genreId, int page, int size) {
        if (page < 0)
            throw new IllegalArgumentException("Page must be greater than 0.");
        if (size < 1 || size > 100)
            throw new IllegalArgumentException("Size must be between 1 and 100.");
        if (genreId != null && genreId <= 0)
            throw new IllegalArgumentException("Genre id must be positive.");
        return transactions.read(
                ()
                        -> catalog.search(keyword == null || keyword.isBlank() ? null : keyword.trim(),
                                status, genreId, page, size));
    }
    public MovieDetailResponseDTO getMovieById(Integer id) {
        requireId(id);
        return transactions.read(() -> {
            Movie m = catalog.find(id, false);
            var d = m.details();
            return new MovieDetailResponseDTO(m.id(), d.title(), d.description(), d.durationMinutes(),
                    d.releaseDate(), d.language(), d.ageRating(), d.posterUrl(), d.trailerUrl(), d.status(),
                    m.createdAt(), m.updatedAt(), catalog.genres(id), catalog.credits(id));
        });
    }
    public MovieResponseDTO createMovie(MovieRequestDTO request) {
        Movie movie = Movie.create(MovieMapping.details(request));
        return transactions.write(() -> MovieMapping.view(catalog.saveDetails(movie)));
    }
    public MovieResponseDTO updateMovie(Integer id, MovieRequestDTO request) {
        var details = MovieMapping.details(request).validated();
        requireId(id);
        return transactions.write(() -> {
            Movie m = catalog.find(id, false);
            m.revise(details);
            return MovieMapping.view(catalog.saveDetails(m));
        });
    }
    public MovieResponseDTO updateMovieStatus(Integer id, MovieStatus status) {
        if (status == null)
            throw new IllegalArgumentException("Movie status is required");
        requireId(id);
        return transactions.write(() -> {
            Movie m = catalog.find(id, false);
            m.changeStatus(status);
            return MovieMapping.view(catalog.saveDetails(m));
        });
    }
    public void expireMovie(Integer id) {
        updateMovieStatus(id, MovieStatus.EXPIRED);
    }
    static void requireId(Integer id) {
        if (id == null || id <= 0)
            throw new IllegalArgumentException("Movie ID must be positive");
    }
}
