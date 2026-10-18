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
public class PasswordOtp {
    private Integer id;
    private Integer userId;
    @ToString.Exclude private String otpHash;
    private PasswordOtpPurpose purpose;
    @Builder.Default private Integer failedAttempts = 0;
    @Builder.Default private OtpDeliveryStatus otpDeliveryStatus = OtpDeliveryStatus.PENDING;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
    private Long version;
}
