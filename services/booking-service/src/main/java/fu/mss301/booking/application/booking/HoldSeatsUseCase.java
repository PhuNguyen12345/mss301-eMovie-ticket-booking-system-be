package fu.mss301.booking.application.booking;

import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.application.exception.SeatUnavailableException;
import fu.mss301.booking.application.port.CinemaCatalog;
import fu.mss301.booking.application.port.MovieCatalog;
import fu.mss301.booking.application.port.MovieCatalog.MovieInfo;
import fu.mss301.booking.application.port.SeatLayoutProvider;
import fu.mss301.booking.application.port.SeatLayoutProvider.PhysicalSeat;
import fu.mss301.booking.application.port.ShowtimeCatalog;
import fu.mss301.booking.application.port.ShowtimeCatalog.SeatTypePrice;
import fu.mss301.booking.application.port.ShowtimeCatalog.ShowtimeSnapshot;
import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.booking.SeatAlreadyTakenException;
import fu.mss301.booking.domain.booking.SeatSelection;
import fu.mss301.booking.domain.booking.TicketDetails;
import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Holds seats for a customer and opens a PENDING_PAYMENT booking. */
public class HoldSeatsUseCase {
    private final ShowtimeCatalog showtimes;
    private final SeatLayoutProvider seatLayout;
    private final MovieCatalog movies;
    private final CinemaCatalog cinemas;
    private final BookingRepository bookings;
    private final HoldPolicy policy;
    private final Transactions transactions;
    private final Clock clock;

    public HoldSeatsUseCase(ShowtimeCatalog showtimes, SeatLayoutProvider seatLayout, MovieCatalog movies,
                            CinemaCatalog cinemas, BookingRepository bookings,
                            HoldPolicy policy, Transactions transactions, Clock clock) {
        this.showtimes = showtimes;
        this.seatLayout = seatLayout;
        this.movies = movies;
        this.cinemas = cinemas;
        this.bookings = bookings;
        this.policy = policy;
        this.transactions = transactions;
        this.clock = clock;
    }

    public BookingView hold(Integer userId, Long showtimeId, List<Integer> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) throw new DomainException("At least one seat is required");
        LocalDateTime now = LocalDateTime.now(clock);

        // Remote reads happen before the transaction so no DB lock is held while waiting on the network.
        ShowtimeSnapshot showtime = showtimes.getShowtime(showtimeId);
        if (!showtime.isOpenForBookingAt(now)) {
            throw new InvalidStateTransitionException("Showtime is not open for booking");
        }
        Map<Integer, PhysicalSeat> layout = seatLayout.getSeats(showtime.screenId()).stream()
            .collect(Collectors.toMap(PhysicalSeat::id, Function.identity(), (first, second) -> first));
        Map<Integer, SeatTypePrice> prices = showtimes.getSeatPrices(showtimeId);
        seatIds.forEach(seatId -> requireUsable(seatId, layout));

        // Only a valid request pays for these extra calls. Ticket details are copied, so reads need no upstream.
        TicketDetails ticket = ticketFor(showtime);
        Map<Integer, String> seatTypes = cinemas.seatTypeNames();
        List<SeatSelection> selections = seatIds.stream()
            .map(seatId -> select(layout.get(seatId), prices, seatTypes, showtime)).toList();

        try {
            return transactions.write(() -> BookingView.of(bookings.save(
                Booking.hold(userId, showtimeId, ticket, selections, policy.maxSeats(), now, policy.holdTtl())), null));
        } catch (SeatAlreadyTakenException exception) {
            throw new SeatUnavailableException("One or more selected seats are no longer available");
        }
    }

    private TicketDetails ticketFor(ShowtimeSnapshot showtime) {
        MovieInfo movie = movies.getMovie(showtime.movieId());
        return new TicketDetails(movie.title(), movie.posterUrl(), cinemas.cinemaName(showtime.cinemaId()),
            cinemas.screenName(showtime.screenId()), showtime.startTime(), showtime.endTime(), showtime.movieId(),
            showtime.cinemaId());
    }

    private static void requireUsable(Integer seatId, Map<Integer, PhysicalSeat> layout) {
        PhysicalSeat seat = seatId == null ? null : layout.get(seatId);
        if (seat == null) throw new DomainException("Seat " + seatId + " does not belong to this showtime's screen");
        if (!seat.usable()) throw new SeatUnavailableException("Seat " + seatId + " is not in service");
    }

    private static SeatSelection select(PhysicalSeat seat, Map<Integer, SeatTypePrice> prices,
                                        Map<Integer, String> seatTypes, ShowtimeSnapshot showtime) {
        SeatTypePrice typePrice = seat.seatTypeId() == null ? null : prices.get(seat.seatTypeId());
        String typeName = seat.seatTypeId() == null ? null : seatTypes.get(seat.seatTypeId());
        return typePrice == null
            ? new SeatSelection(seat.id(), null, showtime.basePrice(), seat.rowCode(), seat.seatNumber(), typeName,
                seat.seatTypeId())
            : new SeatSelection(seat.id(), typePrice.id(), typePrice.price(), seat.rowCode(), seat.seatNumber(), typeName,
                seat.seatTypeId());
    }
}
