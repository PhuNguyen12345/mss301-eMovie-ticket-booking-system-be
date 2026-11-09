package fu.mss301.movie.application.usecase;

import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.movie.application.dto.*;
import fu.mss301.movie.application.exception.*;
import fu.mss301.movie.application.port.in.GenreService;
import fu.mss301.movie.application.port.out.*;
import fu.mss301.movie.domain.model.Genre;
public class GenreUseCase implements GenreService {
    private final ReferenceCatalog catalog;
    private final Transactions transactions;
    public GenreUseCase(ReferenceCatalog catalog, Transactions transactions) {
        this.catalog = catalog;
        this.transactions = transactions;
    }
    public PageResponseDTO<GenreResponseDTO> getGenres(String keyword, int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("Page must be >= 0 and size must be between 1 and 100");
        return transactions.read(
                ()
                        -> catalog.genres(
                                keyword == null || keyword.isBlank() ? null : keyword.trim(), page, size));
    }
    public GenreResponseDTO getGenreById(Integer id) {
        requireId(id);
        return transactions.read(() -> view(catalog.genre(id, false)));
    }
    public GenreResponseDTO createGenre(GenreRequestDTO request) {
        return save(null, request);
    }
    public GenreResponseDTO updateGenre(Integer id, GenreRequestDTO request) {
        if (request == null)
            throw new IllegalArgumentException("Genre name is required");
        Genre value = Genre.create(request.getName(), request.getDescription());
        requireId(id);
        return persist(id, value);
    }
    private GenreResponseDTO save(Integer id, GenreRequestDTO request) {
        if (request == null)
            throw new IllegalArgumentException("Genre name is required");
        return persist(id, Genre.create(request.getName(), request.getDescription()));
    }
    private GenreResponseDTO persist(Integer id, Genre value) {
        return transactions.write(() -> {
            Genre replacement =
                    id == null ? value : catalog.genre(id, false).rename(value.name(), value.description());
            if (catalog.genreNameExists(value.name(), id))
                throw new GenreConflictException("Genre name already exists");
            return view(catalog.save(replacement));
        });
    }
    public void deleteGenre(Integer id) {
        requireId(id);
        transactions.write(() -> {
            catalog.genre(id, true);
            if (catalog.genreInUse(id))
                throw new GenreConflictException("Cannot delete a genre that is used by movies");
            catalog.deleteGenre(id);
            return null;
        });
    }
    private static GenreResponseDTO view(Genre value) {
        return new GenreResponseDTO(value.id(), value.name(), value.description());
    }
    private static void requireId(Integer id) {
        if (id == null || id <= 0)
            throw new IllegalArgumentException("Genre ID must be positive");
    }
}
