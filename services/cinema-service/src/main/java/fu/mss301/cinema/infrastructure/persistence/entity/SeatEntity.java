package fu.mss301.cinema.infrastructure.persistence.entity;

import fu.mss301.cinema.domain.model.FacilityStatus;
import jakarta.persistence.*;

@Entity @Table(name = "seats")
public class SeatEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Integer id;
    @Column(name = "screen_id", nullable = false) public Integer screenId;
    @Column(name = "row_code", nullable = false, length = 8) public String rowCode;
    @Column(name = "seat_number", nullable = false) public Integer seatNumber;
    @Column(name = "seat_type_id", nullable = false) public Integer seatTypeId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) public FacilityStatus status;
}
