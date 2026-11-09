package fu.mss301.movie.application.usecase;

import fu.mss301.movie.application.dto.*;
import fu.mss301.movie.application.port.in.MovieRelationService;
import fu.mss301.movie.application.port.out.*;
import fu.mss301.movie.domain.model.*;
import java.util.List;
public class MovieRelationsUseCase implements MovieRelationService {
    private final MovieCatalog catalog;
    private final Transactions transactions;
    public MovieRelationsUseCase(MovieCatalog catalog, Transactions transactions) {
        this.catalog = catalog;
        this.transactions = transactions;
    }
    public List<GenreResponseDTO> getGenres(Integer id) {
        MovieUseCase.requireId(id);
        return transactions.read(() -> {
            catalog.find(id, false);
            return catalog.genres(id);
        });
    }
    public List<MovieCreditResponseDTO> getCredits(Integer id) {
        MovieUseCase.requireId(id);
        return transactions.read(() -> {
            catalog.find(id, false);
            return catalog.credits(id);
        });
    }
    public List<GenreResponseDTO> replaceGenres(Integer id, MovieGenresRequestDTO request) {
        var genres = Movie.validateGenres(request == null ? null : request.getGenreIds());
        MovieUseCase.requireId(id);
        return transactions.write(() -> {
            Movie movie = catalog.find(id, true);
            catalog.lockGenres(genres);
            movie.replaceGenres(genres);
            catalog.saveGenres(movie);
            return catalog.genres(id);
        });
    }
    public List<MovieCreditResponseDTO> replaceCredits(Integer id, MovieCreditsRequestDTO request) {
        List<Credit> selection = request == null || request.getCredits() == null
                ? null
                : request.getCredits()
                          .stream()
                          .map(c
                                  -> c == null ? null
                                               : new Credit(null, c.getPersonId(), c.getCreditType(),
                                                         c.getCharacterName(), c.getDisplayOrder()))
                          .toList();
        var credits = Movie.validateCredits(selection);
        MovieUseCase.requireId(id);
        return transactions.write(() -> {
            Movie movie = catalog.find(id, true);
            catalog.lockPersons(credits.stream().map(Credit::personId).distinct().sorted().toList());
            movie.replaceCredits(credits);
            catalog.saveCredits(movie);
            return catalog.credits(id);
        });
    }
}
