package fu.mss301.booking.infrastructure.persistence;

import fu.mss301.booking.domain.booking.SeatHoldStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** {@code active_flag} is a generated column and is intentionally not mapped. */
@Entity
@Table(name = "booking_seats")
@Getter
@Setter
@NoArgsConstructor
public class BookingSeatEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private BookingEntity booking;

    @Column(name = "showtime_id", nullable = false, updatable = false)
    private Long showtimeId;

    @Column(name = "showtime_seat_id", updatable = false)
    private Integer showtimeSeatId;

    @Column(name = "seat_id", nullable = false, updatable = false)
    private Integer seatId;

    @Column(name = "price", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "row_code", updatable = false, length = 10)
    private String rowCode;

    @Column(name = "seat_number", updatable = false)
    private Integer seatNumber;

    @Column(name = "seat_type_name", updatable = false, length = 50)
    private String seatTypeName;

    @Column(name = "seat_type_id", updatable = false)
    private Integer seatTypeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SeatHoldStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
