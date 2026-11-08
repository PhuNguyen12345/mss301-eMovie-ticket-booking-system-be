package fu.mss301.booking.infrastructure.persistence;

import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.booking.BookingCheckIn;
import fu.mss301.booking.domain.booking.BookingCode;
import fu.mss301.booking.domain.booking.BookingCombo;
import fu.mss301.booking.domain.booking.BookingSeat;
import fu.mss301.booking.domain.booking.BookingVoucher;
import fu.mss301.booking.domain.booking.VoucherOffer;
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
            entity.getDiscountAmount(), entity.getStatus(), entity.getExpiredAt(), entity.getVersion(), entity.getCreatedAt(),
            entity.getUpdatedAt(), entity.getSeats().stream().map(seat -> BookingSeat.restore(seat.getId(),
                seat.getShowtimeId(), seat.getShowtimeSeatId(), seat.getSeatId(), seat.getPrice(), seat.getRowCode(),
                seat.getSeatNumber(), seat.getSeatTypeName(), seat.getSeatTypeId(), seat.getStatus(), seat.getCreatedAt(),
                seat.getUpdatedAt())).toList(),
            entity.getCombos().stream().map(combo -> BookingCombo.restore(combo.getId(), combo.getCombo().getId(),
                    combo.getName(), combo.getQuantity(), combo.getPrice()))
                .sorted(Comparator.comparing(BookingCombo::comboId)).toList(),
            entity.getVouchers().stream().map(BookingMapper::voucherOf).toList(),
            entity.getCheckIns().stream().map(row -> BookingCheckIn.restore(row.getId(), row.getQuantity(),
                row.getCheckedInBy(), row.getCheckedInAt())).toList(),
            entity.getShowtimeCancelledAt(), entity.getCompensatedAt());
    }

    private static BookingVoucher voucherOf(BookingVoucherEntity row) {
        var offer = new VoucherOffer(row.getVoucherId(), row.getCode(), row.getKind(), row.getDiscountType(),
            row.getDiscountValue(), row.getMinOrderAmount(), row.getSeatTypeId(), row.getComboId(), row.getExpiresOn());
        return BookingVoucher.restore(row.getId(), offer, row.getSeatId(), row.getDiscountAmount(), row.getCreatedAt());
    }

    /** Bookings made before ticket details were stored have no copy. */
    private static TicketDetails ticket(BookingEntity entity) {
        if (entity.getMovieTitle() == null) return null;
        return new TicketDetails(entity.getMovieTitle(), entity.getMoviePosterUrl(), entity.getCinemaName(),
            entity.getScreenName(), entity.getStartTime(), entity.getEndTime(), entity.getMovieId(),
            entity.getCinemaId());
    }

    static BookingEntity newEntity(Booking source) {
        var entity = new BookingEntity();
        entity.setUserId(source.userId());
        entity.setShowtimeId(source.showtimeId());
        entity.setBookingCode(source.bookingCode().value());
        entity.setBookingTime(source.bookingTime());
        entity.setExpiredAt(source.expiredAt());
        entity.setCreatedAt(source.createdAt());
        update(source, entity, ignored -> {
            throw new IllegalStateException("A new booking has no combos");
        });
        if (!source.vouchers().isEmpty()) throw new IllegalStateException("A new booking has no vouchers");
        return entity;
    }

    /**
     * Copies only what can change. Seat rows keep their id and are never deleted. Combo and voucher rows keep
     * their id too, but a combo or voucher removed from the booking loses its row.
     */
    static void update(Booking source, BookingEntity target, IntFunction<ComboEntity> comboReference) {
        copyTicket(source.ticket(), target);
        target.setTotalAmount(source.totalAmount());
        target.setDiscountAmount(source.discountAmount());
        target.setStatus(source.status());
        target.setUpdatedAt(source.updatedAt());
        target.setShowtimeCancelledAt(source.showtimeCancelledAt());
        target.setCompensatedAt(source.compensatedAt());
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
        updateVouchers(source, target);
        addCheckIns(source, target);
    }

    /** Bookings made before ticket details were stored keep empty columns until the ticket is filled in. */
    private static void copyTicket(TicketDetails ticket, BookingEntity target) {
        if (ticket == null) return;
        target.setMovieTitle(ticket.movieTitle());
        target.setMoviePosterUrl(ticket.moviePosterUrl());
        target.setCinemaName(ticket.cinemaName());
        target.setScreenName(ticket.screenName());
        target.setStartTime(ticket.startTime());
        target.setEndTime(ticket.endTime());
        target.setMovieId(ticket.movieId());
        target.setCinemaId(ticket.cinemaId());
    }

    /** Check-ins are history: new ones are added and stored ones never change. */
    private static void addCheckIns(Booking source, BookingEntity target) {
        for (BookingCheckIn checkIn : source.checkIns()) {
            if (checkIn.id() != null) continue;
            var row = new BookingCheckInEntity();
            row.setBooking(target);
            row.setQuantity(checkIn.quantity());
            row.setCheckedInBy(checkIn.checkedInBy());
            row.setCheckedInAt(checkIn.checkedInAt());
            target.getCheckIns().add(row);
        }
    }

    private static void updateVouchers(Booking source, BookingEntity target) {
        var kept = source.vouchers().stream().map(BookingVoucher::id).filter(Objects::nonNull).collect(Collectors.toSet());
        target.getVouchers().removeIf(row -> !kept.contains(row.getId()));
        var rows = target.getVouchers().stream()
            .collect(Collectors.toMap(BookingVoucherEntity::getId, Function.identity()));
        for (BookingVoucher voucher : source.vouchers()) {
            var row = voucher.id() == null ? newVoucher(voucher, target) : rows.get(voucher.id());
            row.setDiscountAmount(voucher.discountAmount());
        }
    }

    private static BookingVoucherEntity newVoucher(BookingVoucher voucher, BookingEntity booking) {
        var row = new BookingVoucherEntity();
        row.setBooking(booking);
        row.setVoucherId(voucher.voucherId());
        row.setCode(voucher.code());
        row.setKind(voucher.kind());
        row.setDiscountType(voucher.discountType());
        row.setDiscountValue(voucher.discountValue());
        row.setMinOrderAmount(voucher.minOrderAmount());
        row.setSeatTypeId(voucher.seatTypeId());
        row.setComboId(voucher.comboId());
        row.setSeatId(voucher.seatId());
        row.setExpiresOn(voucher.expiresOn());
        row.setCreatedAt(voucher.createdAt());
        booking.getVouchers().add(row);
        return row;
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
        row.setSeatTypeId(seat.seatTypeId());
        row.setStatus(seat.status());
        row.setCreatedAt(seat.createdAt());
        row.setUpdatedAt(seat.updatedAt());
        return row;
    }
}
