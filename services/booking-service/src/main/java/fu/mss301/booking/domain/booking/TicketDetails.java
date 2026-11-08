package fu.mss301.booking.domain.booking;

import fu.mss301.booking.domain.shared.DomainException;

import java.time.LocalDateTime;

/**
 * What a ticket shows, copied when seats are held. A sold ticket keeps these values even if the catalog changes.
 *
 * @param movieId  the movie the ticket is for, used to group statistics; null on tickets copied before it was stored
 * @param cinemaId the cinema the ticket is for, used to group statistics; null on tickets copied before it was stored
 */
public record TicketDetails(String movieTitle, String moviePosterUrl, String cinemaName, String screenName,
                            LocalDateTime startTime, LocalDateTime endTime, Integer movieId, Integer cinemaId) {
    public TicketDetails {
        if (movieTitle == null || movieTitle.isBlank()) throw new DomainException("Movie title is required");
        if (cinemaName == null || cinemaName.isBlank()) throw new DomainException("Cinema name is required");
        if (screenName == null || screenName.isBlank()) throw new DomainException("Screen name is required");
        if (startTime == null || endTime == null) throw new DomainException("Showtime period is required");
    }

    /** A ticket without the movie and cinema ids. */
    public TicketDetails(String movieTitle, String moviePosterUrl, String cinemaName, String screenName,
                         LocalDateTime startTime, LocalDateTime endTime) {
        this(movieTitle, moviePosterUrl, cinemaName, screenName, startTime, endTime, null, null);
    }

    public boolean hasCatalogIds() {
        return movieId != null && cinemaId != null;
    }

    /** This ticket with the ids of {@code source}; every value the customer sees stays as it is. */
    TicketDetails withCatalogIdsOf(TicketDetails source) {
        return new TicketDetails(movieTitle, moviePosterUrl, cinemaName, screenName, startTime, endTime,
            source.movieId(), source.cinemaId());
    }
}
