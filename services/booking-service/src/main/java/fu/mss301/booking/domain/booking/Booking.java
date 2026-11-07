package fu.mss301.booking.domain.booking;

import fu.mss301.booking.domain.booking.event.BookingCancelled;
import fu.mss301.booking.domain.booking.event.BookingConfirmed;
import fu.mss301.booking.domain.booking.event.BookingExpired;
import fu.mss301.booking.domain.shared.DomainEvent;
import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Aggregate root for one customer's seat reservation on one showtime.
 * <p>
 * State machine: {@code PENDING_PAYMENT -> CONFIRMED | CANCELLED | EXPIRED}. Seats are HELD while the
 * booking waits for payment, BOOKED once confirmed and RELEASED when it is cancelled or expires.
 */
public final class Booking {
    private final Integer id;
    private final Integer userId;
    private final Long showtimeId;
    private final BookingCode bookingCode;
    private final LocalDateTime bookingTime;
    private final BigDecimal totalAmount;
    private BookingStatus status;
    private final LocalDateTime expiredAt;
    private final Integer version;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private final List<BookingSeat> seats;
    private final List<DomainEvent> events = new ArrayList<>();

    private Booking(Integer id, Integer userId, Long showtimeId, BookingCode bookingCode, LocalDateTime bookingTime,
                    BigDecimal totalAmount, BookingStatus status, LocalDateTime expiredAt, Integer version,
                    LocalDateTime createdAt, LocalDateTime updatedAt, List<BookingSeat> seats) {
        this.id = id;
        this.userId = userId;
        this.showtimeId = showtimeId;
        this.bookingCode = bookingCode;
        this.bookingTime = bookingTime;
        this.totalAmount = totalAmount;
        this.status = status;
        this.expiredAt = expiredAt;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.seats = new ArrayList<>(seats);
    }

    public static Booking hold(Integer userId, Long showtimeId, List<SeatSelection> selections, int maxSeats,
                               LocalDateTime now, Duration holdTtl) {
        if (userId == null || userId <= 0) throw new DomainException("User id must be positive");
        if (showtimeId == null || showtimeId <= 0) throw new DomainException("Showtime id must be positive");
        if (selections == null || selections.isEmpty()) throw new DomainException("At least one seat is required");
        if (selections.size() > maxSeats) throw new DomainException("A booking can hold at most " + maxSeats + " seats");
        if (holdTtl == null || holdTtl.isNegative() || holdTtl.isZero()) throw new DomainException("Hold TTL must be positive");
        var seen = new HashSet<Integer>();
        for (SeatSelection selection : selections) {
            if (!seen.add(selection.seatId())) throw new DomainException("Seat " + selection.seatId() + " is selected twice");
        }

        var seats = selections.stream().map(selection -> BookingSeat.hold(showtimeId, selection, now)).toList();
        BigDecimal total = selections.stream().map(SeatSelection::price)
            .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        return new Booking(null, userId, showtimeId, BookingCode.generate(now), now, total,
            BookingStatus.PENDING_PAYMENT, now.plus(holdTtl), null, now, now, seats);
    }

    /** Rebuilds a stored booking; performs no business validation because old bookings must always load. */
    public static Booking restore(Integer id, Integer userId, Long showtimeId, BookingCode bookingCode,
                                  LocalDateTime bookingTime, BigDecimal totalAmount, BookingStatus status,
                                  LocalDateTime expiredAt, Integer version, LocalDateTime createdAt,
                                  LocalDateTime updatedAt, List<BookingSeat> seats) {
        return new Booking(id, userId, showtimeId, bookingCode, bookingTime, totalAmount, status, expiredAt, version,
            createdAt, updatedAt, seats);
    }

    /** Payment succeeded: the held seats become booked. Allowed until the hold is actually released. */
    public void confirm(LocalDateTime now) {
        requirePending("confirm");
        status = BookingStatus.CONFIRMED;
        seats.forEach(seat -> seat.book(now));
        updatedAt = now;
        events.add(new BookingConfirmed(id));
    }

    public void cancelBy(Integer requesterId, LocalDateTime now) {
        if (!isOwnedBy(requesterId)) throw new InvalidStateTransitionException("Only the booking owner can cancel it");
        requirePending("cancel");
        status = BookingStatus.CANCELLED;
        releaseSeats(now);
        events.add(new BookingCancelled(id));
    }

    public void expire(LocalDateTime now) {
        requirePending("expire");
        if (now.isBefore(expiredAt)) throw new InvalidStateTransitionException("Booking hold has not expired yet");
        status = BookingStatus.EXPIRED;
        releaseSeats(now);
        events.add(new BookingExpired(id));
    }

    /** A new payment may only be started while the seat hold is still valid. */
    public boolean isPayableAt(LocalDateTime now) {
        return status == BookingStatus.PENDING_PAYMENT && now.isBefore(expiredAt);
    }

    public boolean isOwnedBy(Integer requesterId) {
        return userId.equals(requesterId);
    }

    /** Returns the events raised since the last call and forgets them, so each is published once. */
    public List<DomainEvent> pullEvents() {
        var pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }

    public Integer id() { return id; }
    public Integer userId() { return userId; }
    public Long showtimeId() { return showtimeId; }
    public BookingCode bookingCode() { return bookingCode; }
    public LocalDateTime bookingTime() { return bookingTime; }
    public BigDecimal totalAmount() { return totalAmount; }
    public BookingStatus status() { return status; }
    public LocalDateTime expiredAt() { return expiredAt; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
    public List<BookingSeat> seats() { return Collections.unmodifiableList(seats); }

    /** Optimistic-lock token owned by persistence; the domain never interprets it. */
    public Integer version() { return version; }

    private void releaseSeats(LocalDateTime now) {
        seats.forEach(seat -> seat.release(now));
        updatedAt = now;
    }

    private void requirePending(String action) {
        if (status != BookingStatus.PENDING_PAYMENT) {
            throw new InvalidStateTransitionException("Cannot " + action + " a booking in status " + status);
        }
    }
}
