package fu.mss301.movie.infrastructure.config;

import fu.mss301.movie.application.port.out.*;
import fu.mss301.movie.application.usecase.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class UseCaseConfig {
    @Bean
    MovieUseCase movies(MovieCatalog catalog, Transactions tx) {
        return new MovieUseCase(catalog, tx);
    }
    @Bean
    GenreUseCase genres(ReferenceCatalog catalog, Transactions tx) {
        return new GenreUseCase(catalog, tx);
    }
    @Bean
    PersonUseCase persons(ReferenceCatalog catalog, Transactions tx) {
        return new PersonUseCase(catalog, tx);
    }
    @Bean
    MovieRelationsUseCase relations(MovieCatalog catalog, Transactions tx) {
        return new MovieRelationsUseCase(catalog, tx);
    }
    @Bean
    MoviePosterService posters(MovieCatalog catalog, ImageStorage storage, Transactions tx) {
        return new MoviePosterService(catalog, storage, tx);
    }
}
