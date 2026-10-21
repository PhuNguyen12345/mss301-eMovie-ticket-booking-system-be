package fu.mss301.showtime.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeConfig {
    @Bean
    Clock showtimeClock(@Value("${showtime.zone}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
