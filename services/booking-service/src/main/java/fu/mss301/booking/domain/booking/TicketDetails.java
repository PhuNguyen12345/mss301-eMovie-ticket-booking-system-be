package fu.mss301.booking.domain.booking;

import fu.mss301.booking.domain.shared.DomainException;

import java.time.LocalDateTime;

/** What a ticket shows, copied when seats are held. A sold ticket keeps these values even if the catalog changes. */
public record TicketDetails(String movieTitle, String moviePosterUrl, String cinemaName, String screenName,
                            LocalDateTime startTime, LocalDateTime endTime) {
    public TicketDetails {
        if (movieTitle == null || movieTitle.isBlank()) throw new DomainException("Movie title is required");
        if (cinemaName == null || cinemaName.isBlank()) throw new DomainException("Cinema name is required");
        if (screenName == null || screenName.isBlank()) throw new DomainException("Screen name is required");
        if (startTime == null || endTime == null) throw new DomainException("Showtime period is required");
    }
}
