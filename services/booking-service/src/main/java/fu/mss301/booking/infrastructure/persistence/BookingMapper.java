package fu.mss301.booking.infrastructure.persistence;

import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.booking.BookingCode;
import fu.mss301.booking.domain.booking.BookingSeat;

import java.util.function.Function;
import java.util.stream.Collectors;

final class BookingMapper {
    private BookingMapper() {}

    static Booking toDomain(BookingEntity entity) {
        return Booking.restore(entity.getId(), entity.getUserId(), entity.getShowtimeId(),
            new BookingCode(entity.getBookingCode()), entity.getBookingTime(), entity.getTotalAmount(),
            entity.getStatus(), entity.getExpiredAt(), entity.getVersion(), entity.getCreatedAt(),
            entity.getUpdatedAt(), entity.getSeats().stream().map(seat -> BookingSeat.restore(seat.getId(),
                seat.getShowtimeId(), seat.getShowtimeSeatId(), seat.getSeatId(), seat.getPrice(), seat.getStatus(),
                seat.getCreatedAt(), seat.getUpdatedAt())).toList());
    }

    static BookingEntity newEntity(Booking source) {
        var entity = new BookingEntity();
        entity.setUserId(source.userId());
        entity.setShowtimeId(source.showtimeId());
        entity.setBookingCode(source.bookingCode().value());
        entity.setBookingTime(source.bookingTime());
        entity.setTotalAmount(source.totalAmount());
        entity.setExpiredAt(source.expiredAt());
        entity.setCreatedAt(source.createdAt());
        update(source, entity);
        return entity;
    }

    /** Copies only what can change. Existing seat rows keep their id; rows are never deleted. */
    static void update(Booking source, BookingEntity target) {
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
    }

    private static BookingSeatEntity newSeat(BookingSeat seat, BookingEntity booking) {
        var row = new BookingSeatEntity();
        row.setBooking(booking);
        row.setShowtimeId(seat.showtimeId());
        row.setShowtimeSeatId(seat.showtimeSeatId());
        row.setSeatId(seat.seatId());
        row.setPrice(seat.price());
        row.setStatus(seat.status());
        row.setCreatedAt(seat.createdAt());
        row.setUpdatedAt(seat.updatedAt());
        return row;
    }
}
