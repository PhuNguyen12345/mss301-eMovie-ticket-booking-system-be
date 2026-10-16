package fu.mss301.showtime;

import fu.mss301.common.dto.ApiResponse;
import fu.mss301.showtime.client.CinemaClient;
import fu.mss301.showtime.client.MovieClient;
import fu.mss301.showtime.dto.ShowtimeRequestDTO;
import fu.mss301.showtime.enums.ShowtimeStatus;
import fu.mss301.showtime.exception.ShowtimeConflictException;
import fu.mss301.showtime.exception.UpstreamServiceException;
import fu.mss301.showtime.repository.ShowtimeRepository;
import fu.mss301.showtime.service.ShowtimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "spring.datasource.url=${SHOWTIME_TEST_DB_URL}",
    "spring.datasource.username=${SHOWTIME_TEST_DB_USERNAME:showtime}",
    "spring.datasource.password=${SHOWTIME_TEST_DB_PASSWORD:showtime_test}",
    "eureka.client.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "SHOWTIME_TEST_DB_URL", matches = ".+")
class ShowtimeServiceApplicationTests {
    @Autowired private ShowtimeService service;
    @Autowired private ShowtimeRepository repository;
    @MockitoBean private MovieClient movieClient;
    @MockitoBean private CinemaClient cinemaClient;

    private final LocalDateTime start = LocalDate.now().plusDays(30).atTime(18, 0);

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        when(movieClient.getMovie(anyInt())).thenAnswer(call -> ApiResponse.success(
            new MovieClient.MovieInfo(call.getArgument(0), 120, LocalDate.now(), "PRESENTING"), "OK"));
        when(cinemaClient.getScreen(anyInt())).thenAnswer(call -> ApiResponse.success(
            new CinemaClient.ScreenInfo(call.getArgument(0), 1, "ACTIVE"), "OK"));
        when(cinemaClient.getCinema(1)).thenReturn(ApiResponse.success(
            new CinemaClient.CinemaInfo(1, "Ha Noi", "ACTIVE"), "OK"));
    }

    @Test
    void calculatesEndTimeAndAllowsAdjacentShowsButRejectsEveryOverlapShape() {
        var first = service.createShowtime(request(1, start));
        assertEquals(start.plusHours(2), first.endTime());
        for (int offset : List.of(-119, -60, 0, 60, 119)) {
            assertThrows(ShowtimeConflictException.class, () -> service.createShowtime(request(1, start.plusMinutes(offset))));
        }
        service.createShowtime(request(1, start.minusHours(2)));
        service.createShowtime(request(1, start.plusHours(2)));
        service.createShowtime(request(2, start));
        assertEquals(4, repository.count());
    }

    @Test
    void updateExcludesItselfAndCancelledSlotCanBeReused() {
        var first = service.createShowtime(request(1, start));
        assertEquals(first.id(), service.updateShowtime(first.id(), request(1, start)).id());
        var second = service.createShowtime(request(2, start));
        assertThrows(ShowtimeConflictException.class, () -> service.updateShowtime(second.id(), request(1, start)));
        assertEquals(2, service.getShowtimeById(second.id()).screenId());
        assertEquals(ShowtimeStatus.CANCELLED, service.cancelShowtime(first.id()).status());
        assertEquals(ShowtimeStatus.CANCELLED, service.cancelShowtime(first.id()).status());
        assertThrows(ShowtimeConflictException.class, () -> service.updateShowtime(first.id(), request(1, start)));
        service.createShowtime(request(1, start));
    }

    @Test
    void filtersByDateMovieCinemaScreenLocationAndStatus() {
        var first = service.createShowtime(request(1, start));
        service.createShowtime(request(1, start.plusDays(1)));
        service.cancelShowtime(first.id());
        var result = service.getShowtimes(1, 1, 1, start.toLocalDate(), "HA NOI", ShowtimeStatus.CANCELLED, 0, 10);
        assertEquals(1, result.getTotalElements());
        assertEquals(first.id(), result.getData().getFirst().id());
        assertEquals(0, service.getShowtimes(null, null, null, null, "%", null, 0, 10).getTotalElements());
        assertThrows(IllegalArgumentException.class, () -> service.getShowtimes(null, null, null, null, null, null, -1, 10));
    }

    @Test
    void concurrentFirstBookingsOfAnEmptyScreenHaveExactlyOneWinner() throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<String> create = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    service.createShowtime(request(999, start));
                    return "created";
                } catch (ShowtimeConflictException exception) {
                    return "conflict";
                }
            };
            var results = executor.invokeAll(List.of(create, create), 20, TimeUnit.SECONDS);
            var outcomes = List.of(results.get(0).get(), results.get(1).get());
            assertTrue(outcomes.contains("created"));
            assertTrue(outcomes.contains("conflict"));
            assertEquals(1, repository.count());
        }
    }

    @Test
    void concurrentMovesIntoTheSameScreenHaveExactlyOneWinner() throws Exception {
        var first = service.createShowtime(request(1, start));
        var second = service.createShowtime(request(2, start));
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = List.<Callable<String>>of(
                () -> move(first.id(), barrier), () -> move(second.id(), barrier));
            var results = executor.invokeAll(tasks, 20, TimeUnit.SECONDS);
            var outcomes = List.of(results.get(0).get(), results.get(1).get());
            assertTrue(outcomes.contains("updated"));
            assertTrue(outcomes.contains("conflict"));
            assertEquals(1, service.getShowtimes(null, null, 3, null, null, null, 0, 10).getTotalElements());
        }
    }

    @Test
    void rejectsInvalidPricePastDatesAndInvalidReferencesWithoutPersisting() {
        assertThrows(IllegalArgumentException.class, () -> service.createShowtime(request(1, start.minusYears(1))));
        assertThrows(IllegalArgumentException.class, () -> service.createShowtime(
            new ShowtimeRequestDTO(1, 1, 1, start, new BigDecimal("1.001"))));
        when(cinemaClient.getScreen(1)).thenReturn(ApiResponse.success(new CinemaClient.ScreenInfo(1, 2, "ACTIVE"), "OK"));
        assertThrows(IllegalArgumentException.class, () -> service.createShowtime(request(1, start)));
        when(movieClient.getMovie(1)).thenReturn(ApiResponse.success(null, "OK"));
        assertThrows(UpstreamServiceException.class, () -> service.createShowtime(request(1, start)));
        assertEquals(0, repository.count());
    }

    @Test
    void startedShowCannotBeMovedOrCancelled() {
        var created = service.createShowtime(request(1, start));
        var entity = repository.findById(created.id()).orElseThrow();
        entity.setStartTime(start.minusYears(1));
        entity.setEndTime(start.minusYears(1).plusHours(2));
        repository.saveAndFlush(entity);
        assertThrows(ShowtimeConflictException.class, () -> service.updateShowtime(created.id(), request(2, start)));
        assertThrows(ShowtimeConflictException.class, () -> service.cancelShowtime(created.id()));
    }

    private String move(Long id, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try {
            service.updateShowtime(id, request(3, start));
            return "updated";
        } catch (ShowtimeConflictException exception) {
            return "conflict";
        }
    }

    private ShowtimeRequestDTO request(int screenId, LocalDateTime time) {
        return new ShowtimeRequestDTO(1, 1, screenId, time, new BigDecimal("85000"));
    }
}
