package fu.mss301.cinema.domain.model;

import fu.mss301.cinema.domain.exception.CinemaDomainException;

public record Cinema(Integer id, String code, String name, String location, FacilityStatus status) {
    public Cinema {
        if (code == null || !code.matches("[A-Z0-9_-]{1,32}")) throw new CinemaDomainException("Invalid cinema code");
        if (name == null || name.isBlank() || name.length() > 150) throw new CinemaDomainException("Invalid cinema name");
        if (location == null || location.isBlank() || location.length() > 255) throw new CinemaDomainException("Invalid cinema location");
        if (status == null || status == FacilityStatus.MAINTENANCE) throw new CinemaDomainException("Invalid cinema status");
    }
    public Cinema withStatus(FacilityStatus next) { return new Cinema(id, code, name, location, next); }
}
