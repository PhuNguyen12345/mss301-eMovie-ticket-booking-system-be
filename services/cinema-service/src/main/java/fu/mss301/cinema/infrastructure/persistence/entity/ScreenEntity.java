package fu.mss301.cinema.infrastructure.persistence.entity;

import fu.mss301.cinema.domain.model.FacilityStatus;
import jakarta.persistence.*;

@Entity @Table(name = "screens")
public class ScreenEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Integer id;
    @Column(name = "cinema_id", nullable = false) public Integer cinemaId;
    @Column(nullable = false, length = 32) public String code;
    @Column(nullable = false, length = 100) public String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) public FacilityStatus status;
}
