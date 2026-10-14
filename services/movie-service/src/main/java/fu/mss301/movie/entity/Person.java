package fu.mss301.movie.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;

@Entity 
@Table (name = "person")
@Getter 
@Setter 
@NoArgsConstructor 
public class Person {
    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY) 
    @Column (name = "person_id", columnDefinition = "INT UNSIGNED")
    private Integer id;

    @Column (name = "full_name", nullable = false, length = 255)
    private String fullName;
}
