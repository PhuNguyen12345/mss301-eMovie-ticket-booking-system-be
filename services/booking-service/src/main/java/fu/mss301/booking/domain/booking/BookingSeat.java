package fu.mss301.booking.domain.booking;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Child entity of {@link Booking}; only the aggregate root changes its state. */
public final class BookingSeat {
    private final Integer id;
    private final Long showtimeId;
    private final Integer showtimeSeatId;
    private final Integer seatId;
    private final BigDecimal price;
    private final String rowCode;
    private final Integer seatNumber;
    private final String seatTypeName;
    private SeatHoldStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private BookingSeat(Integer id, Long showtimeId, Integer showtimeSeatId, Integer seatId, BigDecimal price,
                        String rowCode, Integer seatNumber, String seatTypeName, SeatHoldStatus status,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.showtimeId = showtimeId;
        this.showtimeSeatId = showtimeSeatId;
        this.seatId = seatId;
        this.price = price;
        this.rowCode = rowCode;
        this.seatNumber = seatNumber;
        this.seatTypeName = seatTypeName;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static BookingSeat hold(Long showtimeId, SeatSelection selection, LocalDateTime now) {
        return new BookingSeat(null, showtimeId, selection.showtimeSeatId(), selection.seatId(), selection.price(),
            selection.rowCode(), selection.seatNumber(), selection.seatTypeName(), SeatHoldStatus.HELD, now, now);
    }

    /** Rebuilds a stored seat; performs no business validation. */
    public static BookingSeat restore(Integer id, Long showtimeId, Integer showtimeSeatId, Integer seatId,
                                      BigDecimal price, String rowCode, Integer seatNumber, String seatTypeName,
                                      SeatHoldStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new BookingSeat(id, showtimeId, showtimeSeatId, seatId, price, rowCode, seatNumber, seatTypeName,
            status, createdAt, updatedAt);
    }

    void book(LocalDateTime now) {
        status = SeatHoldStatus.BOOKED;
        updatedAt = now;
    }

    void release(LocalDateTime now) {
        status = SeatHoldStatus.RELEASED;
        updatedAt = now;
    }

    public Integer id() { return id; }
    public Long showtimeId() { return showtimeId; }
    public Integer showtimeSeatId() { return showtimeSeatId; }
    public Integer seatId() { return seatId; }
    public BigDecimal price() { return price; }
    public String rowCode() { return rowCode; }
    public Integer seatNumber() { return seatNumber; }
    public String seatTypeName() { return seatTypeName; }
    public SeatHoldStatus status() { return status; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
}
