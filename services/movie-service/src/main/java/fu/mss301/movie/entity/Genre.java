package fu.mss301.movie.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.websocket.ClientEndpoint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;

@Entity 
@Table (
    name = "genre",
    uniqueConstraints = {
        @UniqueConstraint (
            name = "uk_genre_name",
            columnNames = {"name"}
        )
    }
)

@Getter 
@Setter 
@NoArgsConstructor 
public class Genre {
    @Id 
    @Column(name = "genre_id", columnDefinition = "INT UNSIGNED")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column (name = "name", nullable = false, length = 100) 
    private String name; 

    @Column (name = "description", length = 500)
    private String description;

}
