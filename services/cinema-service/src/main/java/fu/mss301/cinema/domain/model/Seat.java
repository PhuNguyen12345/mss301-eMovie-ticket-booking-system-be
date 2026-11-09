package fu.mss301.cinema.domain.model;

import fu.mss301.cinema.domain.exception.CinemaDomainException;

public record Seat(Integer id, Integer screenId, String rowCode, Integer seatNumber,
                   Integer seatTypeId, FacilityStatus status) {
    public Seat {
        if (screenId == null || screenId <= 0) throw new CinemaDomainException("Invalid screen ID");
        if (rowCode == null || !rowCode.matches("[A-Z0-9]{1,8}")) throw new CinemaDomainException("Invalid row code");
        if (seatNumber == null || seatNumber <= 0) throw new CinemaDomainException("Invalid seat number");
        if (seatTypeId == null || seatTypeId <= 0) throw new CinemaDomainException("Invalid seat type ID");
        if (status == null) throw new CinemaDomainException("Invalid seat status");
    }
    public Seat withStatus(FacilityStatus next) { return new Seat(id, screenId, rowCode, seatNumber, seatTypeId, next); }
    public Seat visibleIn(FacilityStatus cinema, FacilityStatus screen) {
        return cinema == FacilityStatus.ACTIVE && screen == FacilityStatus.ACTIVE ? this : withStatus(FacilityStatus.INACTIVE);
    }
}
