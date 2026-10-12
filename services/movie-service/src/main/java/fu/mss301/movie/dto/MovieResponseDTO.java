package fu.mss301.movie.dto;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Getter;
import fu.mss301.movie.enums.MovieStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;


@Getter
@Setter 
@NoArgsConstructor 
@AllArgsConstructor 
public class MovieResponseDTO {
    private Integer id;
    private String title;
    private String description;
    private Integer durationMinutes;
    private LocalDate releaseDate;
    private String language;
    private String ageRating;
    private String posterUrl;
    private String trailerUrl;
    private MovieStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
