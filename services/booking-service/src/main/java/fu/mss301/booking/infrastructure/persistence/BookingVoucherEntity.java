package fu.mss301.booking.infrastructure.persistence;

import fu.mss301.booking.domain.voucher.DiscountType;
import fu.mss301.booking.domain.voucher.VoucherKind;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A voucher applied to a booking. Only {@code discountAmount} changes, when the booking is priced again. */
@Entity
@Table(name = "booking_vouchers")
@Getter
@Setter
@NoArgsConstructor
public class BookingVoucherEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private BookingEntity booking;

    @Column(name = "voucher_id", nullable = false, updatable = false)
    private Integer voucherId;

    @Column(name = "code", nullable = false, updatable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 20)
    private VoucherKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, updatable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "min_order_amount", updatable = false, precision = 10, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "seat_type_id", updatable = false)
    private Integer seatTypeId;

    @Column(name = "combo_id", updatable = false)
    private Integer comboId;

    @Column(name = "seat_id", updatable = false)
    private Integer seatId;

    @Column(name = "expires_on", nullable = false, updatable = false)
    private LocalDate expiresOn;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
