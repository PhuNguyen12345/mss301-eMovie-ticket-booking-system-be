package fu.mss301.showtime.service;

import fu.mss301.showtime.dto.SeatPriceRequestDTO;
import fu.mss301.showtime.dto.SeatPriceResponseDTO;
import fu.mss301.showtime.entity.ShowtimeSeatPrice;
import fu.mss301.showtime.exception.ShowtimeNotFoundException;
import fu.mss301.showtime.repository.ShowtimeRepository;
import fu.mss301.showtime.repository.ShowtimeSeatPriceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SeatPriceService {
    private final ShowtimeRepository showtimes;
    private final ShowtimeSeatPriceRepository prices;
    private final Clock clock;

    public SeatPriceService(ShowtimeRepository showtimes, ShowtimeSeatPriceRepository prices, Clock clock) {
        this.showtimes = showtimes;
        this.prices = prices;
        this.clock = clock;
    }

    public List<SeatPriceResponseDTO> getSeatPrices(Long showtimeId) {
        validateId(showtimeId);
        if (!showtimes.existsById(showtimeId)) throw new ShowtimeNotFoundException(showtimeId);
        return prices.findByShowtimeIdOrderBySeatTypeIdAsc(showtimeId).stream().map(this::toResponse).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<SeatPriceResponseDTO> replaceSeatPrices(Long showtimeId, List<SeatPriceRequestDTO> requests) {
        validateId(showtimeId);
        Set<Integer> seatTypes = validateRequests(requests);
        // Lock the parent even when no prices exist, so concurrent replacements cannot interleave.
        showtimes.findByIdForUpdate(showtimeId).orElseThrow(() -> new ShowtimeNotFoundException(showtimeId));
        var existing = prices.findByShowtimeIdOrderBySeatTypeIdAsc(showtimeId);
        var byType = existing.stream().collect(Collectors.toMap(ShowtimeSeatPrice::getSeatTypeId, Function.identity()));
        prices.deleteAll(existing.stream().filter(price -> !seatTypes.contains(price.getSeatTypeId())).toList());
        LocalDateTime now = LocalDateTime.now(clock);
        for (var request : requests) {
            var price = byType.get(request.seatTypeId());
            if (price == null) {
                price = new ShowtimeSeatPrice();
                price.setShowtimeId(showtimeId);
                price.setSeatTypeId(request.seatTypeId());
                price.setCreatedAt(now);
            }
            price.setPrice(request.price().setScale(2));
            price.setUpdatedAt(now);
            prices.save(price);
        }
        prices.flush();
        return prices.findByShowtimeIdOrderBySeatTypeIdAsc(showtimeId).stream().map(this::toResponse).toList();
    }

    private Set<Integer> validateRequests(List<SeatPriceRequestDTO> requests) {
        if (requests == null) throw new IllegalArgumentException("Seat prices are required");
        Set<Integer> seatTypes = new HashSet<>();
        for (var request : requests) {
            if (request == null || request.seatTypeId() == null || request.seatTypeId() <= 0) {
                throw new IllegalArgumentException("Seat type ID must be positive");
            }
            if (!seatTypes.add(request.seatTypeId())) {
                throw new IllegalArgumentException("Duplicate seat type ID: " + request.seatTypeId());
            }
            BigDecimal price = request.price();
            if (price == null || price.signum() < 0 || price.compareTo(new BigDecimal("9999999999.99")) > 0
                    || price.stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException("Price must be non-negative, at most 9999999999.99, with up to 2 decimal places");
            }
        }
        return seatTypes;
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("Showtime ID must be positive");
    }

    private SeatPriceResponseDTO toResponse(ShowtimeSeatPrice price) {
        return new SeatPriceResponseDTO(price.getId(), price.getSeatTypeId(), price.getPrice());
    }
}
