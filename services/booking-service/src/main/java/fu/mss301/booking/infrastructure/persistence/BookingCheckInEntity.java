package fu.mss301.booking.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A scan at the cinema door. Rows are only ever added. */
@Entity
@Table(name = "booking_check_ins")
@Getter
@Setter
@NoArgsConstructor
public class BookingCheckInEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private BookingEntity booking;

    @Column(name = "quantity", nullable = false, updatable = false)
    private Integer quantity;

    @Column(name = "checked_in_by", nullable = false, updatable = false)
    private Integer checkedInBy;

    @Column(name = "checked_in_at", nullable = false, updatable = false)
    private LocalDateTime checkedInAt;
}
