package fu.mss301.movie.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter 
@Setter 
@AllArgsConstructor 
@NoArgsConstructor 
public class MovieCreditsRequestDTO {
    private List<MovieCreditRequestDTO> credits;   
}
