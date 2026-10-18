package fu.mss301.user.domain.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RefreshCredential {
    private Integer id;
    private User user;
    @Builder.Default private Boolean revoked = false;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private LocalDateTime updatedAt;
}
