package fu.mss301.showtime.domain;

import fu.mss301.showtime.domain.exception.ShowtimeConflictException;
import fu.mss301.showtime.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ShowtimeTests {
    private final LocalDateTime now = LocalDateTime.of(2027, 1, 1, 12, 0);
    private final Price basePrice = new Price(new BigDecimal("85000"));

    @Test
    void schedulesAndReschedulesThroughBusinessMethods() {
        var showtime = Showtime.schedule(schedule(now.plusDays(1)), now);
        assertEquals(ShowtimeStatus.SCHEDULED, showtime.status());
        assertEquals(now.plusDays(1).plusHours(2), showtime.schedule().endTime());
        showtime.reschedule(schedule(now.plusDays(2)), now.plusHours(1));
        assertEquals(now.plusDays(2), showtime.schedule().startTime());
        assertEquals(now.plusHours(1), showtime.updatedAt());
    }

    @Test
    void startedAndCancelledShowsCannotBeRescheduled() {
        var showtime = Showtime.schedule(schedule(now.plusDays(1)), now);
        assertThrows(ShowtimeConflictException.class, () -> showtime.reschedule(schedule(now.plusDays(2)), now.plusDays(1)));
        showtime.cancel(now.plusHours(1));
        assertThrows(ShowtimeConflictException.class, () -> showtime.reschedule(schedule(now.plusDays(2)), now.plusHours(2)));
    }

    @Test
    void cancellationIsIdempotentButCannotStartAfterTheShowBegins() {
        var showtime = Showtime.schedule(schedule(now.plusDays(1)), now);
        assertThrows(ShowtimeConflictException.class, () -> showtime.cancel(now.plusDays(1)));
        showtime.cancel(now.plusHours(1));
        showtime.cancel(now.plusDays(2));
        assertEquals(ShowtimeStatus.CANCELLED, showtime.status());
        assertEquals(now.plusHours(1), showtime.updatedAt());
    }

    @Test
    void duplicatePricesCannotPartiallyMutateTheAggregate() {
        var showtime = Showtime.restore(7L, schedule(now.plusDays(1)), ShowtimeStatus.SCHEDULED, now, now,
            List.of(new SeatPrice(77, 1, basePrice, now, now)));
        var original = showtime.seatPrices();
        var selection = new SeatPriceSelection(2, new Price(BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class, () -> showtime.replaceSeatPrices(List.of(selection, selection), now.plusHours(1)));
        assertEquals(original, showtime.seatPrices());
        showtime.replaceSeatPrices(List.of(new SeatPriceSelection(1, new Price(BigDecimal.ZERO))), now.plusHours(2));
        assertEquals(77, showtime.seatPrices().getFirst().id());
        assertEquals(now, showtime.seatPrices().getFirst().createdAt());
        assertEquals(now.plusHours(2), showtime.seatPrices().getFirst().updatedAt());
        showtime.replaceSeatPrices(List.of(), now.plusHours(3));
        assertTrue(showtime.seatPrices().isEmpty());
    }

    @Test
    void callersCannotMutatePricesThroughInputsOrReturnedCollections() {
        var input = new ArrayList<SeatPrice>();
        input.add(new SeatPrice(77, 1, basePrice, now, now));
        var showtime = Showtime.restore(7L, schedule(now.plusDays(1)), ShowtimeStatus.SCHEDULED, now, now, input);
        input.clear();
        assertEquals(1, showtime.seatPrices().size());
        assertThrows(UnsupportedOperationException.class, () -> showtime.seatPrices().clear());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1.001", "10000000000"})
    void rejectsPricesOutsideStorageAndBusinessLimits(String value) {
        assertThrows(IllegalArgumentException.class, () -> new Price(new BigDecimal(value)));
    }

    @Test
    void allowsFreeTicketsAndTheMaximumPrice() {
        assertEquals(new BigDecimal("0.00"), new Price(BigDecimal.ZERO).amount());
        assertEquals(new BigDecimal("9999999999.99"), new Price(new BigDecimal("9999999999.99")).amount());
        assertThrows(IllegalArgumentException.class, () -> new Price(null));
        assertThrows(IllegalArgumentException.class, () -> new SeatPriceSelection(0, basePrice));
    }

    @Test
    void rejectsUnavailableMoviesVenuesAndOverlaps() {
        var venue = new Venue(1, 1, 1, "Ha Noi", true, true);
        var movie = new Movie(1, 120, now.toLocalDate(), "PRESENTING");
        assertThrows(IllegalArgumentException.class, () -> Schedule.plan(
            new Movie(1, 120, now.toLocalDate(), "EXPIRED"), venue, now.plusDays(1), basePrice, now));
        assertThrows(IllegalArgumentException.class, () -> Schedule.plan(
            new Movie(1, 120, now.plusDays(2).toLocalDate(), "WILL_PRESENT"), venue, now.plusDays(1), basePrice, now));
        assertThrows(IllegalArgumentException.class, () -> Schedule.plan(movie,
            new Venue(1, 2, 1, "Ha Noi", true, true), now.plusDays(1), basePrice, now));
        assertThrows(IllegalArgumentException.class, () -> Schedule.plan(movie,
            new Venue(1, 1, 1, "Ha Noi", false, true), now.plusDays(1), basePrice, now));
        assertThrows(IllegalArgumentException.class, () -> Showtime.schedule(schedule(now), now));
        assertThrows(ShowtimeConflictException.class, () -> schedule(now.plusDays(1)).requireAvailable(true));
        assertDoesNotThrow(() -> schedule(now.plusDays(1)).requireAvailable(false));
    }

    @Test
    void priceChangesPreserveExistingLifecycleBehavior() {
        var showtime = Showtime.schedule(schedule(now.plusDays(1)), now);
        showtime.cancel(now.plusHours(1));
        showtime.replaceSeatPrices(List.of(new SeatPriceSelection(1, basePrice)), now.plusHours(2));
        assertEquals(ShowtimeStatus.CANCELLED, showtime.status());
        assertEquals(now.plusHours(1), showtime.updatedAt());
    }

    private Schedule schedule(LocalDateTime start) {
        return new Schedule(1, 1, 1, "Ha Noi", start, start.plusHours(2), basePrice);
    }
}
