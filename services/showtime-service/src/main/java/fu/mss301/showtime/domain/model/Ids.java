package fu.mss301.showtime.domain.model;

public final class Ids {
    private Ids() {}

    public static void requirePositive(Number id, String name) {
        if (id == null || id.longValue() <= 0) throw new IllegalArgumentException(name + " ID must be positive");
    }
}
