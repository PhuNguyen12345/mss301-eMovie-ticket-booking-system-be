package fu.mss301.booking.infrastructure.persistence;

import fu.mss301.booking.domain.booking.BookingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class BookingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Integer userId;

    @Column(name = "showtime_id", nullable = false, updatable = false)
    private Long showtimeId;

    @Column(name = "booking_code", nullable = false, updatable = false, length = 50)
    private String bookingCode;

    @Column(name = "movie_title")
    private String movieTitle;

    @Column(name = "movie_poster_url", length = 500)
    private String moviePosterUrl;

    @Column(name = "cinema_name")
    private String cinemaName;

    @Column(name = "screen_name", length = 100)
    private String screenName;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "movie_id")
    private Integer movieId;

    @Column(name = "cinema_id")
    private Integer cinemaId;

    @Column(name = "booking_time", nullable = false, updatable = false)
    private LocalDateTime bookingTime;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "expired_at", nullable = false, updatable = false)
    private LocalDateTime expiredAt;

    @Column(name = "showtime_cancelled_at")
    private LocalDateTime showtimeCancelledAt;

    @Column(name = "compensated_at")
    private LocalDateTime compensatedAt;

    @Version
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seatId ASC")
    @BatchSize(size = 100)
    private List<BookingSeatEntity> seats = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<BookingComboEntity> combos = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @BatchSize(size = 100)
    private List<BookingVoucherEntity> vouchers = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    @BatchSize(size = 100)
    private List<BookingCheckInEntity> checkIns = new ArrayList<>();
}
