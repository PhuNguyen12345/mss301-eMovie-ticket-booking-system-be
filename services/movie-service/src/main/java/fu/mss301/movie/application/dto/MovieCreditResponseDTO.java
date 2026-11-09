package fu.mss301.movie.application.dto;

import fu.mss301.movie.domain.model.CreditType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MovieCreditResponseDTO {
    private Integer id;
    private Integer personId;
    private String fullName;
    private CreditType creditType;
    private String characterName;
    private Integer displayOrder;
}
