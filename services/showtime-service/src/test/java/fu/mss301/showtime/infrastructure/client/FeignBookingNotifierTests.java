package fu.mss301.showtime.infrastructure.client;

import fu.mss301.common.dto.ApiResponse;
import fu.mss301.showtime.infrastructure.client.BookingClient.CancellationResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeignBookingNotifierTests {
    private final BookingClient client = mock(BookingClient.class);
    private final List<Runnable> scheduled = new ArrayList<>();

    @Test
    void callsBookingServiceInTheBackground() {
        when(client.showtimeCancelled(7L)).thenReturn(
            ApiResponse.success(new CancellationResult(7L, 1, 2, 0), "OK"));
        var notifier = notifier(scheduled::add);

        notifier.showtimeCancelled(7L);
        verifyNoInteractions(client);
        assertEquals(1, scheduled.size());

        scheduled.getFirst().run();
        verify(client).showtimeCancelled(7L);
    }

    @Test
    void failuresInTheBackgroundAreOnlyLogged() {
        when(client.showtimeCancelled(anyLong()))
            .thenThrow(new IllegalStateException("booking-service is down"))
            .thenReturn(null)
            .thenReturn(ApiResponse.success(null, "OK"))
            .thenReturn(ApiResponse.success(new CancellationResult(7L, 0, 1, 1), "OK"));
        var notifier = notifier(scheduled::add);
        for (int i = 0; i < 4; i++) notifier.showtimeCancelled(7L);

        for (var task : scheduled) assertDoesNotThrow(task::run);
        verify(client, times(4)).showtimeCancelled(7L);
    }

    @Test
    void aRejectedTaskDoesNotFailTheCancellation() {
        assertDoesNotThrow(() -> notifier(task -> { throw new RejectedExecutionException(); }).showtimeCancelled(7L));
        verifyNoInteractions(client);
    }

    private FeignBookingNotifier notifier(Executor executor) {
        return new FeignBookingNotifier(client, executor);
    }
}
