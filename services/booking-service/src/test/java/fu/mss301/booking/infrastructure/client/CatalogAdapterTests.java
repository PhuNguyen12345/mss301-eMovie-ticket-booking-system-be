package fu.mss301.booking.infrastructure.client;

import fu.mss301.booking.application.exception.UpstreamServiceException;
import fu.mss301.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogAdapterTests {
    private final MovieClient movieClient = mock(MovieClient.class);
    private final CinemaClient cinemaClient = mock(CinemaClient.class);
    private final MovieCatalogAdapter movies = new MovieCatalogAdapter(movieClient);
    private final CinemaCatalogAdapter cinemas = new CinemaCatalogAdapter(cinemaClient);

    @Test
    void aMovieWithoutAPosterIsValid() {
        when(movieClient.getMovie(1)).thenReturn(ApiResponse.success(new MovieClient.MovieInfo("Dune", null), "ok"));
        assertEquals("Dune", movies.getMovie(1).title());
        assertNull(movies.getMovie(1).posterUrl());
    }

    @Test
    void aMovieWithoutATitleIsAnUpstreamFailure() {
        when(movieClient.getMovie(1)).thenReturn(ApiResponse.success(new MovieClient.MovieInfo(" ", "url"), "ok"));
        assertThrows(UpstreamServiceException.class, () -> movies.getMovie(1));
    }

    @Test
    void cinemaAndScreenNamesAreRequired() {
        when(cinemaClient.getCinema(1)).thenReturn(ApiResponse.success(new CinemaClient.NamedInfo("Rạp 1"), "ok"));
        when(cinemaClient.getScreen(2)).thenReturn(ApiResponse.success(new CinemaClient.NamedInfo(null), "ok"));
        assertEquals("Rạp 1", cinemas.cinemaName(1));
        assertThrows(UpstreamServiceException.class, () -> cinemas.screenName(2));
    }

    @Test
    void seatTypesAreKeyedById() {
        when(cinemaClient.getSeatTypes()).thenReturn(ApiResponse.success(List.of(
            new CinemaClient.SeatTypeInfo(1, "Ghế thường"), new CinemaClient.SeatTypeInfo(2, "Ghế VIP"),
            new CinemaClient.SeatTypeInfo(3, null)), "ok"));
        assertEquals(Map.of(1, "Ghế thường", 2, "Ghế VIP"), cinemas.seatTypeNames());
    }
}
