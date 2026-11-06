package fu.mss301.cinema.infrastructure.persistence.entity;

import fu.mss301.cinema.domain.model.FacilityStatus;
import jakarta.persistence.*;

@Entity @Table(name = "cinemas")
public class CinemaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Integer id;
    @Column(nullable = false, length = 32, unique = true) public String code;
    @Column(nullable = false, length = 150) public String name;
    @Column(nullable = false, length = 255) public String location;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) public FacilityStatus status;
}
