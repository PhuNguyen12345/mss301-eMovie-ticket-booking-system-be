package fu.mss301.showtime.domain.model;

import fu.mss301.showtime.domain.exception.ShowtimeConflictException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Showtime {
    private final Long id;
    private Schedule schedule;
    private ShowtimeStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<SeatPrice> seatPrices;

    private Showtime(Long id, Schedule schedule, ShowtimeStatus status, LocalDateTime createdAt,
                     LocalDateTime updatedAt, List<SeatPrice> seatPrices) {
        if (id != null) Ids.requirePositive(id, "Showtime");
        this.id = id;
        this.schedule = Objects.requireNonNull(schedule);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.seatPrices = List.copyOf(seatPrices);
        if (seatPrices.stream().map(SeatPrice::seatTypeId).distinct().count() != seatPrices.size()) {
            throw new IllegalArgumentException("Duplicate seat type ID");
        }
    }

    public static Showtime schedule(Schedule schedule, LocalDateTime now) {
        Schedule.requireFuture(schedule.startTime(), now);
        return new Showtime(null, schedule, ShowtimeStatus.SCHEDULED, now, now, List.of());
    }

    public static Showtime restore(Long id, Schedule schedule, ShowtimeStatus status, LocalDateTime createdAt,
                                   LocalDateTime updatedAt, List<SeatPrice> prices) {
        Ids.requirePositive(id, "Showtime");
        return new Showtime(id, schedule, status, createdAt, updatedAt, prices);
    }

    public void reschedule(Schedule replacement, LocalDateTime now) {
        requireEditable(now);
        Schedule.requireFuture(replacement.startTime(), now);
        schedule = replacement;
        updatedAt = now;
    }

    public void cancel(LocalDateTime now) {
        if (status == ShowtimeStatus.CANCELLED) return;
        requireEditable(now);
        status = ShowtimeStatus.CANCELLED;
        updatedAt = now;
    }

    public void replaceSeatPrices(List<SeatPriceSelection> selections, LocalDateTime now) {
        if (selections == null) throw new IllegalArgumentException("Seat prices are required");
        var types = new HashSet<Integer>();
        for (var selection : selections) {
            if (selection == null) throw new IllegalArgumentException("Seat price is required");
            if (!types.add(selection.seatTypeId())) throw new IllegalArgumentException("Duplicate seat type ID: " + selection.seatTypeId());
        }
        var existing = seatPrices.stream().collect(Collectors.toMap(SeatPrice::seatTypeId, Function.identity()));
        seatPrices = selections.stream().map(selection -> {
            var current = existing.get(selection.seatTypeId());
            return current == null ? SeatPrice.create(selection, now) : current.reprice(selection.price(), now);
        }).sorted(Comparator.comparing(SeatPrice::seatTypeId)).toList();
    }

    public void requireEditable(LocalDateTime now) {
        if (status == ShowtimeStatus.CANCELLED || !schedule.startTime().isAfter(now)) {
            throw new ShowtimeConflictException("Cancelled or started showtimes cannot be changed");
        }
    }

    public Long id() { return id; }
    public Schedule schedule() { return schedule; }
    public ShowtimeStatus status() { return status; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
    public List<SeatPrice> seatPrices() { return seatPrices; }
}
