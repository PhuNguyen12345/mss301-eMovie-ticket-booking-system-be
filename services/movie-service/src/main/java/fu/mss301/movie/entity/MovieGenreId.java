package fu.mss301.movie.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;

@Embeddable 
@Getter 
@Setter 
@NoArgsConstructor 
@AllArgsConstructor 
@EqualsAndHashCode 
public class MovieGenreId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column (
        name = "movie_id", 
        nullable = false, 
        columnDefinition = "INT UNSIGNED"
    )
    private Integer movieId;

    @Column (
        name = "genre_id", 
        nullable = false, 
        columnDefinition = "INT UNSIGNED"
    )
    private Integer genreId;

}
