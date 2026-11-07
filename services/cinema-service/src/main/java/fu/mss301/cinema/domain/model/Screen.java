package fu.mss301.cinema.domain.model;

import fu.mss301.cinema.domain.exception.CinemaDomainException;

public record Screen(Integer id, Integer cinemaId, String code, String name, FacilityStatus status) {
    public Screen {
        if (cinemaId == null || cinemaId <= 0) throw new CinemaDomainException("Invalid cinema ID");
        if (code == null || !code.matches("[A-Z0-9_-]{1,32}")) throw new CinemaDomainException("Invalid screen code");
        if (name == null || name.isBlank() || name.length() > 100) throw new CinemaDomainException("Invalid screen name");
        if (status == null) throw new CinemaDomainException("Invalid screen status");
    }
    public Screen withStatus(FacilityStatus next) { return new Screen(id, cinemaId, code, name, next); }
}
