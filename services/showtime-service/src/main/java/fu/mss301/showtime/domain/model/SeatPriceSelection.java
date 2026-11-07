package fu.mss301.showtime.domain.model;

public record SeatPriceSelection(Integer seatTypeId, Price price) {
    public SeatPriceSelection {
        Ids.requirePositive(seatTypeId, "Seat type");
        if (price == null) throw new IllegalArgumentException("Price is required");
    }
}
