package fu.mss301.booking.infrastructure.config;

import fu.mss301.booking.application.booking.HoldPolicy;
import fu.mss301.booking.domain.booking.CheckInWindow;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class BookingConfig {
    @Bean
    Clock bookingClock(BookingProperties properties) {
        return Clock.system(ZoneId.of(properties.zone()));
    }

    @Bean
    HoldPolicy holdPolicy(BookingProperties properties) {
        return new HoldPolicy(properties.holdTtl(), properties.maxSeats());
    }

    @Bean
    CheckInWindow checkInWindow(BookingProperties properties) {
        return new CheckInWindow(properties.checkInOpensBefore(), properties.checkInClosesAfter());
    }
}
