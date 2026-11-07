package fu.mss301.booking.infrastructure.persistence;

import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.booking.BookingCode;
import fu.mss301.booking.domain.booking.BookingCombo;
import fu.mss301.booking.domain.booking.BookingSeat;
import fu.mss301.booking.domain.booking.TicketDetails;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

final class BookingMapper {
    private BookingMapper() {}

    static Booking toDomain(BookingEntity entity) {
        return Booking.restore(entity.getId(), entity.getUserId(), entity.getShowtimeId(),
            new BookingCode(entity.getBookingCode()), ticket(entity), entity.getBookingTime(), entity.getTotalAmount(),
            entity.getStatus(), entity.getExpiredAt(), entity.getVersion(), entity.getCreatedAt(),
            entity.getUpdatedAt(), entity.getSeats().stream().map(seat -> BookingSeat.restore(seat.getId(),
                seat.getShowtimeId(), seat.getShowtimeSeatId(), seat.getSeatId(), seat.getPrice(), seat.getRowCode(),
                seat.getSeatNumber(), seat.getSeatTypeName(), seat.getStatus(), seat.getCreatedAt(),
                seat.getUpdatedAt())).toList(),
            entity.getCombos().stream().map(combo -> BookingCombo.restore(combo.getId(), combo.getCombo().getId(),
                    combo.getName(), combo.getQuantity(), combo.getPrice()))
                .sorted(Comparator.comparing(BookingCombo::comboId)).toList());
    }

    /** Bookings made before ticket details were stored have no copy. */
    private static TicketDetails ticket(BookingEntity entity) {
        if (entity.getMovieTitle() == null) return null;
        return new TicketDetails(entity.getMovieTitle(), entity.getMoviePosterUrl(), entity.getCinemaName(),
            entity.getScreenName(), entity.getStartTime(), entity.getEndTime());
    }

    static BookingEntity newEntity(Booking source) {
        var entity = new BookingEntity();
        entity.setUserId(source.userId());
        entity.setShowtimeId(source.showtimeId());
        entity.setBookingCode(source.bookingCode().value());
        var ticket = source.ticket();
        entity.setMovieTitle(ticket.movieTitle());
        entity.setMoviePosterUrl(ticket.moviePosterUrl());
        entity.setCinemaName(ticket.cinemaName());
        entity.setScreenName(ticket.screenName());
        entity.setStartTime(ticket.startTime());
        entity.setEndTime(ticket.endTime());
        entity.setBookingTime(source.bookingTime());
        entity.setExpiredAt(source.expiredAt());
        entity.setCreatedAt(source.createdAt());
        update(source, entity, ignored -> {
            throw new IllegalStateException("A new booking has no combos");
        });
        return entity;
    }

    /**
     * Copies only what can change. Seat rows keep their id and are never deleted. Combo rows keep their id
     * too, but a combo removed from the booking loses its row.
     */
    static void update(Booking source, BookingEntity target, IntFunction<ComboEntity> comboReference) {
        target.setTotalAmount(source.totalAmount());
        target.setStatus(source.status());
        target.setUpdatedAt(source.updatedAt());
        var rows = target.getSeats().stream()
            .collect(Collectors.toMap(BookingSeatEntity::getId, Function.identity()));
        for (BookingSeat seat : source.seats()) {
            if (seat.id() == null) {
                target.getSeats().add(newSeat(seat, target));
            } else {
                var row = rows.get(seat.id());
                row.setStatus(seat.status());
                row.setUpdatedAt(seat.updatedAt());
            }
        }
        updateCombos(source, target, comboReference);
    }

    private static void updateCombos(Booking source, BookingEntity target, IntFunction<ComboEntity> comboReference) {
        var kept = source.combos().stream().map(BookingCombo::id).filter(Objects::nonNull).collect(Collectors.toSet());
        target.getCombos().removeIf(row -> !kept.contains(row.getId()));
        var rows = target.getCombos().stream()
            .collect(Collectors.toMap(BookingComboEntity::getId, Function.identity()));
        for (BookingCombo combo : source.combos()) {
            var row = combo.id() == null ? newCombo(combo, target, comboReference) : rows.get(combo.id());
            row.setName(combo.name());
            row.setQuantity(combo.quantity());
            row.setPrice(combo.price());
        }
    }

    private static BookingComboEntity newCombo(BookingCombo combo, BookingEntity booking,
                                               IntFunction<ComboEntity> comboReference) {
        var row = new BookingComboEntity();
        row.setBooking(booking);
        row.setCombo(comboReference.apply(combo.comboId()));
        booking.getCombos().add(row);
        return row;
    }

    private static BookingSeatEntity newSeat(BookingSeat seat, BookingEntity booking) {
        var row = new BookingSeatEntity();
        row.setBooking(booking);
        row.setShowtimeId(seat.showtimeId());
        row.setShowtimeSeatId(seat.showtimeSeatId());
        row.setSeatId(seat.seatId());
        row.setPrice(seat.price());
        row.setRowCode(seat.rowCode());
        row.setSeatNumber(seat.seatNumber());
        row.setSeatTypeName(seat.seatTypeName());
        row.setStatus(seat.status());
        row.setCreatedAt(seat.createdAt());
        row.setUpdatedAt(seat.updatedAt());
        return row;
    }
}
