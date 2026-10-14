package fu.mss301.movie.dto;
import fu.mss301.movie.enums.MovieStatus;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor 

public class MovieRequestDTO {
    private String title;
    private String description; 
    private Integer durationMinutes; 
    private LocalDate releaseDate; 
    private String language; 
    private String ageRating; 
    private String posterUrl; 
    private String trailerUrl; 
    private MovieStatus status;
}
