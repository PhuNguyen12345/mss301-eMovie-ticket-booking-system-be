package fu.mss301.cinema.infrastructure.persistence.entity;

import jakarta.persistence.*;

@Entity @Table(name = "seat_types")
public class SeatTypeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Integer id;
    @Column(nullable = false, length = 32, unique = true) public String code;
    @Column(nullable = false, length = 100) public String name;
}
