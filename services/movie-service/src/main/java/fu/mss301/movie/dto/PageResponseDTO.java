package fu.mss301.movie.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter 
@Setter 
@AllArgsConstructor 
@NoArgsConstructor
public class PageResponseDTO<T> {
    private T data;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first; 
    private boolean last; 
}
