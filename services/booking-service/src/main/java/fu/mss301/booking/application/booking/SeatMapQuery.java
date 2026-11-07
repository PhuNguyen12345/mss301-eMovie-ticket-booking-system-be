package fu.mss301.booking.application.booking;

import fu.mss301.booking.application.booking.SeatMapView.Availability;
import fu.mss301.booking.application.booking.SeatMapView.SeatAvailability;
import fu.mss301.booking.application.port.BookingQueries;
import fu.mss301.booking.application.port.SeatLayoutProvider;
import fu.mss301.booking.application.port.ShowtimeCatalog;
import fu.mss301.booking.application.port.ShowtimeCatalog.SeatTypePrice;
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.domain.booking.SeatHoldStatus;

import java.util.Map;
import java.util.stream.Collectors;

/** Physical layout from cinema-service merged with this service's live seat state for one showtime. */
public class SeatMapQuery {
    private final ShowtimeCatalog showtimes;
    private final SeatLayoutProvider seatLayout;
    private final BookingQueries queries;
    private final Transactions transactions;

    public SeatMapQuery(ShowtimeCatalog showtimes, SeatLayoutProvider seatLayout, BookingQueries queries,
                        Transactions transactions) {
        this.showtimes = showtimes;
        this.seatLayout = seatLayout;
        this.queries = queries;
        this.transactions = transactions;
    }

    public SeatMapView seatMap(Long showtimeId) {
        var showtime = showtimes.getShowtime(showtimeId);
        Map<Integer, SeatTypePrice> prices = showtimes.getSeatPrices(showtimeId);
        Map<Integer, SeatHoldStatus> occupied = transactions.read(() -> queries.findOccupiedSeats(showtimeId)).stream()
            .collect(Collectors.toMap(OccupiedSeat::seatId, OccupiedSeat::status, (first, second) -> first));
        var seats = seatLayout.getSeats(showtime.screenId()).stream().map(seat -> {
            SeatTypePrice typePrice = seat.seatTypeId() == null ? null : prices.get(seat.seatTypeId());
            Availability status;
            if (!seat.usable()) status = Availability.UNAVAILABLE;
            else if (occupied.get(seat.id()) == SeatHoldStatus.BOOKED) status = Availability.BOOKED;
            else if (occupied.get(seat.id()) == SeatHoldStatus.HELD) status = Availability.HELD;
            else status = Availability.AVAILABLE;
            return new SeatAvailability(seat.id(), seat.rowCode(), seat.seatNumber(), seat.seatTypeId(),
                typePrice == null ? showtime.basePrice() : typePrice.price(), status);
        }).toList();
        return new SeatMapView(showtimeId, showtime.screenId(), seats);
    }
}
