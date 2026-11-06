package fu.mss301.showtime.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "showtime_seat_prices", uniqueConstraints =
    @UniqueConstraint(name = "uq_showtime_seat_type", columnNames = {"showtime_id", "seat_type_id"}))
@Getter
@Setter
@NoArgsConstructor
public class SeatPriceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "showtime_id", nullable = false, updatable = false)
    private Long showtimeId;

    @Column(name = "seat_type_id", nullable = false, updatable = false)
    private Integer seatTypeId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
