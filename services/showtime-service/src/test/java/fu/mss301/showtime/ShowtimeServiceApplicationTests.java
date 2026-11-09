package fu.mss301.showtime;

import fu.mss301.common.dto.ApiResponse;
import fu.mss301.showtime.application.port.BookingNotifier;
import fu.mss301.showtime.infrastructure.client.CinemaClient;
import fu.mss301.showtime.infrastructure.client.MovieClient;
import fu.mss301.showtime.application.dto.ShowtimeCommand;
import fu.mss301.showtime.application.dto.SeatPriceCommand;
import fu.mss301.showtime.domain.model.ShowtimeStatus;
import fu.mss301.showtime.domain.exception.ShowtimeConflictException;
import fu.mss301.showtime.application.exception.ShowtimeNotFoundException;
import fu.mss301.showtime.application.exception.UpstreamServiceException;
import fu.mss301.showtime.infrastructure.persistence.ShowtimeJpaRepository;
import fu.mss301.showtime.infrastructure.persistence.SeatPriceJpaRepository;
import fu.mss301.showtime.application.usecase.SeatPriceService;
import fu.mss301.showtime.application.usecase.ShowtimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.sql.SQLException;
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
    @Autowired private ShowtimeJpaRepository repository;
    @Autowired private SeatPriceJpaRepository seatPrices;
    @Autowired private SeatPriceService seatPriceService;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private MovieClient movieClient;
    @MockitoBean private CinemaClient cinemaClient;
    @MockitoBean private BookingNotifier bookingNotifier;

    private final LocalDateTime start = LocalDate.now().plusDays(30).atTime(18, 0);

    @BeforeEach
    void setUp() {
        seatPrices.deleteAll();
        repository.deleteAll();
        when(movieClient.getMovie(anyInt())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "Movie lookup must run outside a transaction");
            return ApiResponse.success(new MovieClient.MovieInfo(call.getArgument(0), 120, LocalDate.now(), "PRESENTING"), "OK");
        });
        when(cinemaClient.getScreen(anyInt())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "Cinema lookup must run outside a transaction");
            return ApiResponse.success(new CinemaClient.ScreenInfo(call.getArgument(0), 1, "ACTIVE"), "OK");
        });
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
    void cancellingNotifiesBookingServiceAfterTheCommitAndAgainOnRepeat() {
        var created = service.createShowtime(request(1, start));
        doAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                "Booking service must be notified after the commit");
            assertEquals(ShowtimeStatus.CANCELLED, repository.findById(created.id()).orElseThrow().getStatus());
            return null;
        }).when(bookingNotifier).showtimeCancelled(created.id());

        service.cancelShowtime(created.id());
        service.cancelShowtime(created.id());

        verify(bookingNotifier, times(2)).showtimeCancelled(created.id());
    }

    @Test
    void failedCancellationDoesNotNotifyBookingService() {
        var created = service.createShowtime(request(1, start));
        var entity = repository.findById(created.id()).orElseThrow();
        entity.setStartTime(start.minusYears(1));
        entity.setEndTime(start.minusYears(1).plusHours(2));
        repository.saveAndFlush(entity);

        assertThrows(ShowtimeConflictException.class, () -> service.cancelShowtime(created.id()));
        assertThrows(ShowtimeNotFoundException.class, () -> service.cancelShowtime(999_999_999L));

        verifyNoInteractions(bookingNotifier);
    }

    @Test
    void filtersByDateMovieCinemaScreenLocationAndStatus() {
        var first = service.createShowtime(request(1, start));
        service.createShowtime(request(1, start.plusDays(1)));
        service.cancelShowtime(first.id());
        var result = service.getShowtimes(1, 1, 1, start.toLocalDate(), "HA NOI", ShowtimeStatus.CANCELLED, 0, 10);
        assertEquals(1, result.totalElements());
        assertEquals(first.id(), result.data().getFirst().id());
        assertEquals(0, service.getShowtimes(null, null, null, null, "%", null, 0, 10).totalElements());
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
            assertEquals(1, service.getShowtimes(null, null, 3, null, null, null, 0, 10).totalElements());
        }
    }

    @Test
    void rejectsInvalidPricePastDatesAndInvalidReferencesWithoutPersisting() {
        assertThrows(IllegalArgumentException.class, () -> service.createShowtime(request(1, start.minusYears(1))));
        assertThrows(IllegalArgumentException.class, () -> service.createShowtime(
            new ShowtimeCommand(1, 1, 1, start, new BigDecimal("1.001"))));
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

    @Test
    void seatPricesPersistReplaceAndClearWhileRetainingExistingRowIds() {
        var showtime = service.createShowtime(request(1, start));
        var originalShowtime = service.getShowtimeById(showtime.id());
        assertTrue(seatPriceService.getSeatPrices(showtime.id()).isEmpty());
        var first = seatPriceService.replaceSeatPrices(showtime.id(), List.of(
            new SeatPriceCommand(2, new BigDecimal("150000")),
            new SeatPriceCommand(1, new BigDecimal("90000"))));
        assertEquals(List.of(1, 2), first.stream().map(p -> p.seatTypeId()).toList());
        assertEquals(first, seatPriceService.getSeatPrices(showtime.id()));
        var original = seatPrices.findById(first.getFirst().id()).orElseThrow();
        var replacement = List.of(new SeatPriceCommand(1, new BigDecimal("95000")),
            new SeatPriceCommand(3, BigDecimal.ZERO));
        var updated = seatPriceService.replaceSeatPrices(showtime.id(), replacement);
        assertEquals(first.getFirst().id(), updated.getFirst().id());
        assertEquals(new BigDecimal("95000.00"), seatPriceService.getSeatPrices(showtime.id()).getFirst().price());
        assertEquals(original.getCreatedAt(), seatPrices.findById(first.getFirst().id()).orElseThrow().getCreatedAt());
        assertEquals(List.of(1, 3), updated.stream().map(p -> p.seatTypeId()).toList());
        assertEquals(updated, seatPriceService.replaceSeatPrices(showtime.id(), replacement));
        assertEquals(originalShowtime, service.getShowtimeById(showtime.id()));
        assertTrue(seatPriceService.replaceSeatPrices(showtime.id(), List.of()).isEmpty());
        assertTrue(seatPriceService.getSeatPrices(showtime.id()).isEmpty());
    }

    @Test
    void invalidReplacementLeavesPricesIntactAndOtherShowtimesAreIsolated() {
        var first = service.createShowtime(request(1, start));
        var second = service.createShowtime(request(2, start));
        seatPriceService.replaceSeatPrices(first.id(), List.of(new SeatPriceCommand(1, new BigDecimal("90000"))));
        var before = seatPriceService.getSeatPrices(first.id());
        assertThrows(IllegalArgumentException.class, () -> seatPriceService.replaceSeatPrices(first.id(), List.of(
            new SeatPriceCommand(2, BigDecimal.TEN), new SeatPriceCommand(3, new BigDecimal("-1")))));
        seatPriceService.replaceSeatPrices(second.id(), List.of(new SeatPriceCommand(1, BigDecimal.ZERO)));
        assertEquals(before, seatPriceService.getSeatPrices(first.id()));
        assertEquals(0, seatPriceService.getSeatPrices(second.id()).getFirst().price().signum());
    }

    @Test
    void migrationEnforcesUniqueTypeForeignKeyAndNonNegativePrice() {
        var showtime = service.createShowtime(request(1, start));
        seatPriceService.replaceSeatPrices(showtime.id(), List.of(new SeatPriceCommand(1, BigDecimal.TEN)));
        String insert = """
            INSERT INTO showtime_seat_prices (showtime_id, seat_type_id, price, created_at, updated_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            """;
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(insert, showtime.id(), 1, 20));
        var negativePrice = assertThrows(DataAccessException.class, () -> jdbc.update(insert, showtime.id(), 2, -1));
        assertEquals(3819, assertInstanceOf(SQLException.class, negativePrice.getCause()).getErrorCode());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(insert, Long.MAX_VALUE, 1, 10));
        assertEquals(1, seatPrices.count());
    }

    @Test
    void scheduleAndPriceChangesPersistThroughTheSameAggregateWithoutLosingPrices() {
        var showtime = service.createShowtime(request(1, start));
        seatPriceService.replaceSeatPrices(showtime.id(), List.of(new SeatPriceCommand(1, new BigDecimal("90000"))));
        var pricesBefore = seatPriceService.getSeatPrices(showtime.id());
        service.updateShowtime(showtime.id(), request(2, start.plusHours(3)));
        assertEquals(pricesBefore, seatPriceService.getSeatPrices(showtime.id()));
        assertEquals(2, service.getShowtimeById(showtime.id()).screenId());
        service.cancelShowtime(showtime.id());
        assertEquals(pricesBefore, seatPriceService.getSeatPrices(showtime.id()));
    }

    @Test
    void concurrentInitialPriceReplacementsRemainAtomic() throws Exception {
        var showtime = service.createShowtime(request(1, start));
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = List.<Callable<Void>>of(
                () -> replacePrices(showtime.id(), 2, barrier),
                () -> replacePrices(showtime.id(), 3, barrier));
            for (var result : executor.invokeAll(tasks, 20, TimeUnit.SECONDS)) result.get();
        }
        var result = seatPriceService.getSeatPrices(showtime.id());
        assertEquals(2, result.size());
        assertEquals(1, result.getFirst().seatTypeId());
        int lastType = result.getLast().seatTypeId();
        assertTrue(lastType == 2 || lastType == 3);
        assertEquals(0, BigDecimal.valueOf(lastType).compareTo(result.getFirst().price()));
        assertEquals(0, BigDecimal.valueOf(lastType).compareTo(result.getLast().price()));
    }

    private Void replacePrices(Long id, int type, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        seatPriceService.replaceSeatPrices(id, List.of(new SeatPriceCommand(1, BigDecimal.valueOf(type)),
            new SeatPriceCommand(type, BigDecimal.valueOf(type))));
        return null;
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

    private ShowtimeCommand request(int screenId, LocalDateTime time) {
        return new ShowtimeCommand(1, 1, screenId, time, new BigDecimal("85000"));
    }
}
