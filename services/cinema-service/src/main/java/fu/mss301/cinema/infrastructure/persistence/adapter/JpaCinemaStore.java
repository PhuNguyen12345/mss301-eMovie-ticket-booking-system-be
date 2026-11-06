package fu.mss301.cinema.infrastructure.persistence.adapter;

import fu.mss301.cinema.domain.model.*;
import fu.mss301.cinema.domain.repository.CinemaStore;
import fu.mss301.cinema.infrastructure.persistence.entity.*;
import fu.mss301.cinema.infrastructure.persistence.repository.*;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaCinemaStore implements CinemaStore {
    private final CinemaJpaRepository cinemas;
    private final ScreenJpaRepository screens;
    private final SeatTypeJpaRepository types;
    private final SeatJpaRepository seats;

    public JpaCinemaStore(CinemaJpaRepository cinemas, ScreenJpaRepository screens,
                          SeatTypeJpaRepository types, SeatJpaRepository seats) {
        this.cinemas = cinemas; this.screens = screens; this.types = types; this.seats = seats;
    }

    public Optional<Cinema> cinema(int id) { return cinemas.findById(id).map(this::toDomain); }
    public List<Cinema> cinemas() { return cinemas.findAll(org.springframework.data.domain.Sort.by("id")).stream().map(this::toDomain).toList(); }
    public Optional<Screen> screen(int id) { return screens.findById(id).map(this::toDomain); }
    public List<Screen> screens(int cinemaId) { return screens.findByCinemaIdOrderById(cinemaId).stream().map(this::toDomain).toList(); }
    public Optional<SeatType> seatType(int id) { return types.findById(id).map(this::toDomain); }
    public List<SeatType> seatTypes() { return types.findAll(org.springframework.data.domain.Sort.by("id")).stream().map(this::toDomain).toList(); }
    public Optional<Seat> seat(int id) { return seats.findById(id).map(this::toDomain); }
    public List<Seat> seats(int screenId) { return seats.findByScreenIdOrderByRowCodeAscSeatNumberAsc(screenId).stream().map(this::toDomain).toList(); }

    public Cinema save(Cinema value) {
        CinemaEntity entity = value.id() == null ? new CinemaEntity() : cinemas.findById(value.id()).orElseThrow();
        entity.code = value.code(); entity.name = value.name(); entity.location = value.location(); entity.status = value.status();
        return toDomain(cinemas.saveAndFlush(entity));
    }
    public Screen save(Screen value) {
        ScreenEntity entity = value.id() == null ? new ScreenEntity() : screens.findById(value.id()).orElseThrow();
        entity.cinemaId = value.cinemaId(); entity.code = value.code(); entity.name = value.name(); entity.status = value.status();
        return toDomain(screens.saveAndFlush(entity));
    }
    public SeatType save(SeatType value) {
        SeatTypeEntity entity = value.id() == null ? new SeatTypeEntity() : types.findById(value.id()).orElseThrow();
        entity.code = value.code(); entity.name = value.name();
        return toDomain(types.saveAndFlush(entity));
    }
    public Seat save(Seat value) {
        SeatEntity entity = value.id() == null ? new SeatEntity() : seats.findById(value.id()).orElseThrow();
        entity.screenId = value.screenId(); entity.rowCode = value.rowCode(); entity.seatNumber = value.seatNumber();
        entity.seatTypeId = value.seatTypeId(); entity.status = value.status();
        return toDomain(seats.saveAndFlush(entity));
    }

    private Cinema toDomain(CinemaEntity e) { return new Cinema(e.id, e.code, e.name, e.location, e.status); }
    private Screen toDomain(ScreenEntity e) { return new Screen(e.id, e.cinemaId, e.code, e.name, e.status); }
    private SeatType toDomain(SeatTypeEntity e) { return new SeatType(e.id, e.code, e.name); }
    private Seat toDomain(SeatEntity e) { return new Seat(e.id, e.screenId, e.rowCode, e.seatNumber, e.seatTypeId, e.status); }
}
