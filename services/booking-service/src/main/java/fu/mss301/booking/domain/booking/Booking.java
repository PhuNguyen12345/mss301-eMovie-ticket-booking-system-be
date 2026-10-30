package fu.mss301.booking.domain.booking;

import fu.mss301.booking.domain.booking.event.BookingCancelled;
import fu.mss301.booking.domain.booking.event.BookingConfirmed;
import fu.mss301.booking.domain.booking.event.BookingExpired;
import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.AbstractAggregateRoot;

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
@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking extends AbstractAggregateRoot<Booking> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Integer userId;

    @Column(name = "showtime_id", nullable = false, updatable = false)
    private Long showtimeId;

    @Embedded
    private BookingCode bookingCode;

    @Column(name = "booking_time", nullable = false, updatable = false)
    private LocalDateTime bookingTime;

    @Column(name = "total_amount", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "expired_at", nullable = false, updatable = false)
    private LocalDateTime expiredAt;

    @Version
    @Getter(AccessLevel.NONE)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seatId ASC")
    @Getter(AccessLevel.NONE)
    private List<BookingSeat> seats = new ArrayList<>();

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

        Booking booking = new Booking();
        booking.userId = userId;
        booking.showtimeId = showtimeId;
        booking.bookingCode = BookingCode.generate(now);
        booking.bookingTime = now;
        booking.status = BookingStatus.PENDING_PAYMENT;
        booking.expiredAt = now.plus(holdTtl);
        booking.createdAt = now;
        booking.updatedAt = now;
        selections.forEach(selection -> booking.seats.add(BookingSeat.hold(booking, showtimeId, selection, now)));
        booking.totalAmount = selections.stream().map(SeatSelection::price)
            .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        return booking;
    }

    /** Payment succeeded: the held seats become booked. Allowed until the hold is actually released. */
    public void confirm(LocalDateTime now) {
        requirePending("confirm");
        status = BookingStatus.CONFIRMED;
        seats.forEach(seat -> seat.book(now));
        updatedAt = now;
        registerEvent(new BookingConfirmed(id));
    }

    public void cancelBy(Integer requesterId, LocalDateTime now) {
        if (!isOwnedBy(requesterId)) throw new InvalidStateTransitionException("Only the booking owner can cancel it");
        requirePending("cancel");
        status = BookingStatus.CANCELLED;
        releaseSeats(now);
        registerEvent(new BookingCancelled(id));
    }

    public void expire(LocalDateTime now) {
        requirePending("expire");
        if (now.isBefore(expiredAt)) throw new InvalidStateTransitionException("Booking hold has not expired yet");
        status = BookingStatus.EXPIRED;
        releaseSeats(now);
        registerEvent(new BookingExpired(id));
    }

    /** A new payment may only be started while the seat hold is still valid. */
    public boolean isPayableAt(LocalDateTime now) {
        return status == BookingStatus.PENDING_PAYMENT && now.isBefore(expiredAt);
    }

    public boolean isOwnedBy(Integer requesterId) {
        return userId.equals(requesterId);
    }

    public List<BookingSeat> getSeats() {
        return Collections.unmodifiableList(seats);
    }

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
