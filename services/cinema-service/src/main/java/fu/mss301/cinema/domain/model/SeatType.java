package fu.mss301.cinema.domain.model;

import fu.mss301.cinema.domain.exception.CinemaDomainException;

public record SeatType(Integer id, String code, String name) {
    public SeatType {
        if (code == null || !code.matches("[A-Z0-9_-]{1,32}")) throw new CinemaDomainException("Invalid seat type code");
        if (name == null || name.isBlank() || name.length() > 100) throw new CinemaDomainException("Invalid seat type name");
    }
}
