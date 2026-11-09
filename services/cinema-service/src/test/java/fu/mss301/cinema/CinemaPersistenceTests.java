package fu.mss301.cinema;

import static org.junit.jupiter.api.Assertions.*;
import fu.mss301.cinema.application.port.in.CinemaCatalog;
import fu.mss301.cinema.application.port.in.CinemaManagement;
import fu.mss301.cinema.domain.model.FacilityStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.url=${CINEMA_TEST_DB_URL}",
    "spring.datasource.username=${CINEMA_TEST_DB_USERNAME:root}",
    "spring.datasource.password=${CINEMA_TEST_DB_PASSWORD:architecture_test}",
    "eureka.client.enabled=false", "cinema.seed.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "CINEMA_TEST_DB_URL", matches = ".+")
class CinemaPersistenceTests {
    @Autowired CinemaCatalog catalog;
    @Autowired CinemaManagement management;

    @Test
    void configuredUseCasesPersistChangesAndApplySeatVisibility() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        var cinema = management.createCinema("C" + suffix, "Cinema", "Ha Noi");
        var screen = management.createScreen(cinema.id(), "S" + suffix, "Screen");
        var type = management.createSeatType("T" + suffix, "Standard");
        var seat = management.createSeat(screen.id(), "A", 1, type.id());
        assertEquals(FacilityStatus.ACTIVE, catalog.listSeats(screen.id()).getFirst().status());
        management.setScreenStatus(screen.id(), FacilityStatus.MAINTENANCE);
        assertEquals(FacilityStatus.INACTIVE, catalog.listSeats(screen.id()).getFirst().status());
        assertEquals(FacilityStatus.ACTIVE, catalog.getSeat(seat.id()).status());
        management.setScreenStatus(screen.id(), FacilityStatus.ACTIVE);
        assertEquals(FacilityStatus.ACTIVE, catalog.listSeats(screen.id()).getFirst().status());
    }
}
