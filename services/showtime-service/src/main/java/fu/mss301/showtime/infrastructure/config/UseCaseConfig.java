package fu.mss301.showtime.infrastructure.config;

import fu.mss301.showtime.application.port.*;
import fu.mss301.showtime.application.usecase.*;
import fu.mss301.showtime.domain.repository.ShowtimeRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class UseCaseConfig {
    @Bean
    ShowtimeService showtimeService(ShowtimeRepository showtimes, ShowtimeQueries queries, ScreenSchedule screens,
            MovieCatalog movies, CinemaCatalog cinemas, Transactions transactions, Clock clock) {
        return new ShowtimeServiceImpl(showtimes, queries, screens, movies, cinemas, transactions, clock);
    }

    @Bean
    SeatPriceService seatPriceService(ShowtimeRepository showtimes, Transactions transactions, Clock clock) {
        return new SeatPriceService(showtimes, transactions, clock);
    }
}
