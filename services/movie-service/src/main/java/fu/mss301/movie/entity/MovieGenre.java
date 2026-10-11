package fu.mss301.movie.entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;


@Entity
@Table(name = "movie_genre")
@Getter 
@NoArgsConstructor 
public class MovieGenre {
    @EmbeddedId
    private MovieGenreId id = new MovieGenreId();

    @MapsId ("movieId")
    @ManyToOne (fetch = FetchType.LAZY, optional = false)
    @JoinColumn (name = "movie_id", nullable = false)
    private Movie movie;

    @MapsId ("genreId")
    @ManyToOne (fetch = FetchType.LAZY, optional = false)
    @JoinColumn (name = "genre_id", nullable = false)
    private Genre genre;

    public MovieGenre (Movie movie, Genre genre) {
        this.movie = movie;
        this.genre = genre;
        this.id.setMovieId(movie.getId());
        this.id.setGenreId(genre.getId());
    }
}
