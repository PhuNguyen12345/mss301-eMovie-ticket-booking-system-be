package fu.mss301.booking.domain.booking;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Child entity of {@link Booking}; only the aggregate root changes its state. */
@Entity
@Table(name = "booking_seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    @Getter(AccessLevel.NONE)
    private Booking booking;

    @Column(name = "showtime_id", nullable = false, updatable = false)
    private Long showtimeId;

    @Column(name = "showtime_seat_id", updatable = false)
    private Integer showtimeSeatId;

    @Column(name = "seat_id", nullable = false, updatable = false)
    private Integer seatId;

    @Column(name = "price", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SeatHoldStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    static BookingSeat hold(Booking booking, Long showtimeId, SeatSelection selection, LocalDateTime now) {
        BookingSeat seat = new BookingSeat();
        seat.booking = booking;
        seat.showtimeId = showtimeId;
        seat.showtimeSeatId = selection.showtimeSeatId();
        seat.seatId = selection.seatId();
        seat.price = selection.price();
        seat.status = SeatHoldStatus.HELD;
        seat.createdAt = now;
        seat.updatedAt = now;
        return seat;
    }

    void book(LocalDateTime now) {
        status = SeatHoldStatus.BOOKED;
        updatedAt = now;
    }

    void release(LocalDateTime now) {
        status = SeatHoldStatus.RELEASED;
        updatedAt = now;
    }
}
