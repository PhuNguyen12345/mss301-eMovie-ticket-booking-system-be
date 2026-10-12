package fu.mss301.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Getter 
@Setter 
@AllArgsConstructor 
@NoArgsConstructor
public class PageResponseDTO<T> {
    private List<T> data;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first; 
    private boolean last; 
}
