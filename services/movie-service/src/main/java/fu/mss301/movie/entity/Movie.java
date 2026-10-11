package fu.mss301.movie.entity;

import java.time.LocalDate;

import fu.mss301.movie.enums.MovieStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity 
@Table(name = "movie")
@Getter 
@Setter 
@NoArgsConstructor 
public class Movie {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column (name = "movie_id", columnDefinition = "INT UNSIGNED")
    private Integer id;
    
    @Column(name = "title", nullable = false, length = 255) 
    private String title;

    @Column (name = "description", columnDefinition = "TEXT")
    private String description;

    @Column (name = "duration_minutes", nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer durationMinutes;

    @Column (name = "release_date", nullable = false)
    private LocalDate releaseDate;

    @Column (name = "language", length = 50) 
    private String language;

    @Column (name = "age_rating", length = 20)
    private String ageRating;

    @Column (name = "poster_url", length = 500)
    private String posterUrl;

    @Column (name = "trailer_url", length = 500)
    private String trailerUrl;

    @Enumerated(EnumType.STRING)
    @Column (name = "status", nullable = false, length = 20)
    private MovieStatus status;

    @Column (name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column (name = "updated_at", nullable = false, insertable = false)
    private LocalDateTime updatedAt;
}
