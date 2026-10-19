package fu.mss301.showtime.service.impl;

import feign.FeignException;
import fu.mss301.common.dto.ApiResponse;
import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.showtime.client.CinemaClient;
import fu.mss301.showtime.client.MovieClient;
import fu.mss301.showtime.dto.ShowtimeRequestDTO;
import fu.mss301.showtime.dto.ShowtimeResponseDTO;
import fu.mss301.showtime.entity.Showtime;
import fu.mss301.showtime.enums.ShowtimeStatus;
import fu.mss301.showtime.exception.*;
import fu.mss301.showtime.repository.ScreenScheduleLockRepository;
import fu.mss301.showtime.repository.ShowtimeRepository;
import fu.mss301.showtime.service.ShowtimeService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.function.Supplier;

@Service
@Transactional(readOnly = true)
public class ShowtimeServiceImpl implements ShowtimeService {
    private final ShowtimeRepository repository;
    private final ScreenScheduleLockRepository scheduleLocks;
    private final MovieClient movieClient;
    private final CinemaClient cinemaClient;
    private final Clock clock;

    public ShowtimeServiceImpl(ShowtimeRepository repository, ScreenScheduleLockRepository scheduleLocks,
            MovieClient movieClient, CinemaClient cinemaClient, Clock clock) {
        this.repository = repository;
        this.scheduleLocks = scheduleLocks;
        this.movieClient = movieClient;
        this.cinemaClient = cinemaClient;
        this.clock = clock;
    }

    @Override
    public PageResponseDTO<ShowtimeResponseDTO> getShowtimes(Integer movieId, Integer cinemaId,
            Integer screenId, LocalDate date, String location, ShowtimeStatus status, int page, int size) {
        if (movieId != null) positiveId(movieId, "Movie");
        if (cinemaId != null) positiveId(cinemaId, "Cinema");
        if (screenId != null) positiveId(screenId, "Screen");
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100");
        }
        if (date != null && (date.getYear() < 1000 || date.getYear() > 9998)) {
            throw new IllegalArgumentException("Date is outside the supported range");
        }
        String normalizedLocation = null;
        if (location != null && !location.isBlank()) {
            if (location.trim().length() > 255) {
                throw new IllegalArgumentException("Location must not exceed 255 characters");
            }
            normalizedLocation = "%" + location.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        }
        var result = repository.searchShowtimes(movieId, cinemaId, screenId, status,
            date == null ? null : date.atStartOfDay(),
            date == null ? null : date.plusDays(1).atStartOfDay(), normalizedLocation,
            PageRequest.of(page, size, Sort.by("startTime", "id"))).map(this::toResponse);
        return new PageResponseDTO<>(result.getContent(), result.getNumber(), result.getSize(),
            result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Override
    public ShowtimeResponseDTO getShowtimeById(Long id) {
        positiveId(id, "Showtime");
        return toResponse(repository.findById(id).orElseThrow(() -> new ShowtimeNotFoundException(id)));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShowtimeResponseDTO createShowtime(ShowtimeRequestDTO request) {
        validateRequest(request);
        var details = resolveDetails(request);
        scheduleLocks.lock(request.screenId());
        requireFuture(request.startTime());
        checkOverlap(request, details.endTime(), null);
        Showtime showtime = new Showtime();
        applyRequest(showtime, request, details);
        showtime.setStatus(ShowtimeStatus.SCHEDULED);
        showtime.setCreatedAt(LocalDateTime.now(clock));
        return toResponse(repository.saveAndFlush(showtime));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShowtimeResponseDTO updateShowtime(Long id, ShowtimeRequestDTO request) {
        positiveId(id, "Showtime");
        validateRequest(request);
        var details = resolveDetails(request);
        Showtime showtime = repository.findByIdForUpdate(id)
            .orElseThrow(() -> new ShowtimeNotFoundException(id));
        requireEditable(showtime);
        int oldScreen = showtime.getScreenId();
        int newScreen = request.screenId();
        scheduleLocks.lock(Math.min(oldScreen, newScreen));
        if (oldScreen != newScreen) scheduleLocks.lock(Math.max(oldScreen, newScreen));
        requireEditable(showtime);
        requireFuture(request.startTime());
        checkOverlap(request, details.endTime(), id);
        applyRequest(showtime, request, details);
        repository.flush();
        return toResponse(showtime);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShowtimeResponseDTO cancelShowtime(Long id) {
        positiveId(id, "Showtime");
        Showtime showtime = repository.findByIdForUpdate(id)
            .orElseThrow(() -> new ShowtimeNotFoundException(id));
        if (showtime.getStatus() == ShowtimeStatus.CANCELLED) return toResponse(showtime);
        scheduleLocks.lock(showtime.getScreenId());
        requireEditable(showtime);
        showtime.setStatus(ShowtimeStatus.CANCELLED);
        showtime.setUpdatedAt(LocalDateTime.now(clock));
        repository.flush();
        return toResponse(showtime);
    }

    private void validateRequest(ShowtimeRequestDTO request) {
        if (request == null) throw new IllegalArgumentException("Showtime request is required");
        positiveId(request.movieId(), "Movie");
        positiveId(request.cinemaId(), "Cinema");
        positiveId(request.screenId(), "Screen");
        requireFuture(request.startTime());
        BigDecimal price = request.basePrice();
        if (price == null || price.signum() < 0 || price.compareTo(new BigDecimal("9999999999.99")) > 0
                || price.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Base price must be non-negative, at most 9999999999.99, with up to 2 decimal places");
        }
    }

    private ScheduleDetails resolveDetails(ShowtimeRequestDTO request) {
        var movie = upstream(() -> movieClient.getMovie(request.movieId()), "Movie");
        if (!request.movieId().equals(movie.id()) || movie.durationMinutes() == null
                || movie.durationMinutes() <= 0 || movie.releaseDate() == null
                || !("PRESENTING".equals(movie.status()) || "WILL_PRESENT".equals(movie.status())
                    || "EXPIRED".equals(movie.status()))) {
            throw new UpstreamServiceException("Movie service returned invalid movie data");
        }
        if ("EXPIRED".equals(movie.status()) || request.startTime().toLocalDate().isBefore(movie.releaseDate())) {
            throw new IllegalArgumentException("Movie must be available and released by the showtime date");
        }
        var screen = upstream(() -> cinemaClient.getScreen(request.screenId()), "Screen");
        var cinema = upstream(() -> cinemaClient.getCinema(request.cinemaId()), "Cinema");
        if (!request.screenId().equals(screen.id()) || !request.cinemaId().equals(cinema.id())
                || screen.cinemaId() == null || screen.status() == null || cinema.status() == null
                || cinema.location() == null || cinema.location().isBlank() || cinema.location().length() > 255) {
            throw new UpstreamServiceException("Cinema service returned invalid cinema or screen data");
        }
        if (!request.cinemaId().equals(screen.cinemaId())) {
            throw new IllegalArgumentException("Screen does not belong to the requested cinema");
        }
        if (!"ACTIVE".equals(screen.status()) || !"ACTIVE".equals(cinema.status())) {
            throw new IllegalArgumentException("Cinema and screen must be active");
        }
        LocalDateTime endTime = request.startTime().plusMinutes(movie.durationMinutes());
        if (endTime.getYear() > 9999) throw new IllegalArgumentException("Showtime end is outside the supported range");
        return new ScheduleDetails(endTime, cinema.location());
    }

    private <T> T upstream(Supplier<ApiResponse<T>> call, String resource) {
        try {
            ApiResponse<T> response = call.get();
            if (response == null || response.getStatus() != 200 || response.getData() == null) {
                throw new UpstreamServiceException(resource + " service returned an invalid response");
            }
            return response.getData();
        } catch (FeignException exception) {
            if (exception.status() == 404) throw new IllegalArgumentException(resource + " does not exist");
            throw new UpstreamServiceException(resource + " service is unavailable");
        }
    }

    private void checkOverlap(ShowtimeRequestDTO request, LocalDateTime endTime, Long excludedId) {
        if (repository.countOverlaps(request.screenId(), request.startTime(), endTime,
                excludedId, ShowtimeStatus.SCHEDULED) > 0) {
            throw new ShowtimeConflictException("Screen already has a showtime in this time range");
        }
    }

    private void requireEditable(Showtime showtime) {
        if (showtime.getStatus() == ShowtimeStatus.CANCELLED || !showtime.getStartTime().isAfter(LocalDateTime.now(clock))) {
            throw new ShowtimeConflictException("Cancelled or started showtimes cannot be changed");
        }
    }

    private void requireFuture(LocalDateTime startTime) {
        if (startTime == null || !startTime.isAfter(LocalDateTime.now(clock)) || startTime.getYear() > 9998
                || startTime.getNano() % 1000 != 0) {
            throw new IllegalArgumentException("Start time must be in the future, before year 9999, with at most microsecond precision");
        }
    }

    private void positiveId(Number id, String name) {
        if (id == null || id.longValue() <= 0) throw new IllegalArgumentException(name + " ID must be positive");
    }

    private void applyRequest(Showtime showtime, ShowtimeRequestDTO request, ScheduleDetails details) {
        showtime.setMovieId(request.movieId());
        showtime.setCinemaId(request.cinemaId());
        showtime.setScreenId(request.screenId());
        showtime.setCinemaLocation(details.location());
        showtime.setStartTime(request.startTime());
        showtime.setEndTime(details.endTime());
        showtime.setBasePrice(request.basePrice());
        showtime.setUpdatedAt(LocalDateTime.now(clock));
    }

    private ShowtimeResponseDTO toResponse(Showtime showtime) {
        return new ShowtimeResponseDTO(showtime.getId(), showtime.getMovieId(), showtime.getCinemaId(),
            showtime.getScreenId(), showtime.getCinemaLocation(), showtime.getStartTime(), showtime.getEndTime(),
            showtime.getBasePrice(), showtime.getStatus(), showtime.getCreatedAt(), showtime.getUpdatedAt());
    }

    private record ScheduleDetails(LocalDateTime endTime, String location) {}
}
