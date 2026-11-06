package fu.mss301.showtime.domain.model;

public record Venue(Integer screenId, Integer screenCinemaId, Integer cinemaId, String location,
                    boolean screenActive, boolean cinemaActive) {
    public Venue {
        Ids.requirePositive(screenId, "Screen");
        Ids.requirePositive(screenCinemaId, "Screen cinema");
        Ids.requirePositive(cinemaId, "Cinema");
        if (location == null || location.isBlank() || location.length() > 255) {
            throw new IllegalArgumentException("Invalid cinema location");
        }
    }

    public void requireBookable() {
        if (!screenCinemaId.equals(cinemaId)) throw new IllegalArgumentException("Screen does not belong to the requested cinema");
        if (!screenActive || !cinemaActive) throw new IllegalArgumentException("Cinema and screen must be active");
    }
}
