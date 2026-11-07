package fu.mss301.booking.application.booking;

import java.util.List;
import java.util.function.Function;

/** One page of results, free of any framework paging type. */
public record BookingPage<T>(List<T> content, int page, int size, long totalElements, int totalPages,
                             boolean first, boolean last) {
    public <R> BookingPage<R> map(Function<T, R> mapper) {
        return new BookingPage<>(content.stream().map(mapper).toList(), page, size, totalElements, totalPages,
            first, last);
    }
}
