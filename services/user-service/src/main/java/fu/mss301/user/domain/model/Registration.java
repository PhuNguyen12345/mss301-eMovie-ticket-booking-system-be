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
public class Registration {
    private String id;
    private String fullName;
    private String email;
    private String phone;
    @ToString.Exclude private String passwordHash;
    @ToString.Exclude private String otpHash;
    private LocalDateTime expiresAt;
    private LocalDateTime nextOtpSendAt;
    @Builder.Default private Integer failedAttempts = 0;
    @Builder.Default private Integer resendCount = 0;
    @Builder.Default private OtpDeliveryStatus otpDeliveryStatus = OtpDeliveryStatus.PENDING;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;
}
