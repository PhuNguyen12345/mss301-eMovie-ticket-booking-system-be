package fu.mss301.movie.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import fu.mss301.movie.enums.CreditType;

@Entity 
@Table (name = "movie_credit")
@Getter
@Setter  
@NoArgsConstructor 
public class MovieCredit {
    @Id 
    @GeneratedValue (strategy = GenerationType.IDENTITY) 
    @Column (
        name = "credit_id", 
        columnDefinition = "INT UNSIGNED")
    private Integer id;

    @ManyToOne (
        fetch = FetchType.LAZY, 
        optional = false
    )
    @JoinColumn (
        name = "movie_id", 
        nullable = false, 
        columnDefinition = "INT UNSIGNED"
    )
    private Movie movie;

    @ManyToOne (
        fetch = FetchType.LAZY, 
        optional = false
    )
    @JoinColumn (
        name = "person_id", 
        nullable = false, 
        columnDefinition = "INT UNSIGNED"
    )
    private Person person;

    @Enumerated (EnumType.STRING)
    @Column (
        name = "credit_type", 
        nullable = false
    )
    private CreditType creditType;

    @Column (
        name = "character_name", 
        length = 255
    )
    private String characterName;

    @Column (
        name = "display_order", 
        columnDefinition = "INT UNSIGNED"
    )
    private Integer displayOrder;
}
