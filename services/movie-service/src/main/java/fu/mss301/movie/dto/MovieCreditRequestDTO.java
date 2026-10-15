package fu.mss301.movie.dto;

import fu.mss301.movie.enums.CreditType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter 
@Setter
@AllArgsConstructor 
@NoArgsConstructor  
public class MovieCreditRequestDTO {
    private Integer personId; 
    private CreditType creditType; 
    private String characterName;
    private Integer displayOrder; 
}
