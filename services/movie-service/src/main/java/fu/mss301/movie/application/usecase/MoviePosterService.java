package fu.mss301.movie.application.usecase;

import fu.mss301.movie.application.dto.*;
import fu.mss301.movie.application.exception.*;
import fu.mss301.movie.application.port.out.*;
import java.io.IOException;
public class MoviePosterService {
    private final MovieCatalog catalog;
    private final ImageStorage images;
    private final Transactions transactions;
    private static final System.Logger log = System.getLogger(MoviePosterService.class.getName());
    public MoviePosterService(MovieCatalog catalog, ImageStorage images, Transactions transactions) {
        this.catalog = catalog;
        this.images = images;
        this.transactions = transactions;
    }
    public MoviePosterResponseDTO uploadPoster(Integer id, byte[] bytes) throws IOException {
        validateMovie(id);
        return replace(id, images.uploadImage(bytes));
    }
    public MoviePosterResponseDTO uploadPosterFromUrl(Integer id, MoviePosterUrlRequestDTO request)
            throws IOException {
        if (request == null)
            throw new IllegalArgumentException("Poster URL request is required");
        validateMovie(id);
        return replace(id, images.uploadImageFromUrl(request.getUrl()));
    }
    private void validateMovie(Integer id) {
        if (id == null || id <= 0)
            throw new IllegalArgumentException("Movie Id must be positive");
        if (!transactions.read(() -> catalog.exists(id)))
            throw new MovieNotFoundException(id);
    }
    private MoviePosterResponseDTO replace(Integer id, ImageUploadResult uploaded) {
        String old;
        try {
            old = transactions.write(() -> {
                var movie = catalog.find(id, true);
                String previous = movie.replacePoster(uploaded.secureUrl(), uploaded.publicId());
                catalog.savePoster(movie);
                return previous;
            });
        } catch (UncertainCommitException exception) {
            log.log(System.Logger.Level.WARNING, () -> "Poster commit needs reconciliation for movie " + id);
            throw exception;
        } catch (RuntimeException exception) {
            deleteQuietly(uploaded.publicId());
            throw exception;
        }
        deleteQuietly(old);
        return new MoviePosterResponseDTO(id, uploaded.secureUrl());
    }
    private void deleteQuietly(String id) {
        if (id == null || id.isBlank())
            return;
        try {
            images.deleteImage(id);
        } catch (Exception exception) {
            log.log(System.Logger.Level.WARNING, "Could not clean up image " + id, exception);
        }
    }
}
