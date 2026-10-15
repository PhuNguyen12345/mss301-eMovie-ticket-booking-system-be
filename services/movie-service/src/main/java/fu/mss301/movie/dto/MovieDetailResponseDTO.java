package fu.mss301.movie.dto;

import fu.mss301.movie.enums.MovieStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MovieDetailResponseDTO {
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
    private List<GenreResponseDTO> genres;
    private List<MovieCreditResponseDTO> credits;
}
