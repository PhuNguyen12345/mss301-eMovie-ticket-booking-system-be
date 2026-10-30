package fu.mss301.booking.application.booking;

import fu.mss301.booking.application.TransactionRunner;
import fu.mss301.booking.application.exception.SeatUnavailableException;
import fu.mss301.booking.application.port.SeatLayoutProvider;
import fu.mss301.booking.application.port.SeatLayoutProvider.PhysicalSeat;
import fu.mss301.booking.application.port.ShowtimeCatalog;
import fu.mss301.booking.application.port.ShowtimeCatalog.SeatTypePrice;
import fu.mss301.booking.application.port.ShowtimeCatalog.ShowtimeSnapshot;
import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.booking.SeatSelection;
import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Holds seats for a customer and opens a PENDING_PAYMENT booking. */
@Service
public class HoldSeatsUseCase {
    private final ShowtimeCatalog showtimes;
    private final SeatLayoutProvider seatLayout;
    private final BookingRepository bookings;
    private final HoldPolicy policy;
    private final TransactionRunner tx;
    private final Clock clock;

    public HoldSeatsUseCase(ShowtimeCatalog showtimes, SeatLayoutProvider seatLayout, BookingRepository bookings,
                            HoldPolicy policy, TransactionRunner tx, Clock clock) {
        this.showtimes = showtimes;
        this.seatLayout = seatLayout;
        this.bookings = bookings;
        this.policy = policy;
        this.tx = tx;
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
        List<SeatSelection> selections = seatIds.stream()
            .map(seatId -> priceSeat(seatId, layout, prices, showtime)).toList();

        try {
            return tx.call(() -> BookingView.of(bookings.save(
                Booking.hold(userId, showtimeId, selections, policy.maxSeats(), now, policy.holdTtl())), null));
        } catch (DataIntegrityViolationException exception) {
            // uq_booking_seats_active: another booking holds or owns one of these seats.
            throw new SeatUnavailableException("One or more selected seats are no longer available");
        }
    }

    private SeatSelection priceSeat(Integer seatId, Map<Integer, PhysicalSeat> layout,
                                    Map<Integer, SeatTypePrice> prices, ShowtimeSnapshot showtime) {
        PhysicalSeat seat = seatId == null ? null : layout.get(seatId);
        if (seat == null) throw new DomainException("Seat " + seatId + " does not belong to this showtime's screen");
        if (!seat.usable()) throw new SeatUnavailableException("Seat " + seatId + " is not in service");
        SeatTypePrice typePrice = seat.seatTypeId() == null ? null : prices.get(seat.seatTypeId());
        return typePrice == null
            ? new SeatSelection(seatId, null, showtime.basePrice())
            : new SeatSelection(seatId, typePrice.id(), typePrice.price());
    }
}
