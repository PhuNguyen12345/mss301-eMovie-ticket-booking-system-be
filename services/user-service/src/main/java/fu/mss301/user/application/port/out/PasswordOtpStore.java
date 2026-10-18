package fu.mss301.user.application.port.out;

import fu.mss301.user.domain.model.PasswordOtp;
import fu.mss301.user.domain.model.PasswordOtpPurpose;
import fu.mss301.user.domain.model.User;
import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordOtpStore {
    Optional<PasswordOtp> findTopByUser_IdAndPurposeOrderByCreatedAtDescIdDesc(Integer userId, PasswordOtpPurpose purpose);
    PasswordOtp saveAndFlush(PasswordOtp otp);
    PasswordOtp createAndFlush(NewPasswordOtp otp);

    record NewPasswordOtp(User user, String otpHash, PasswordOtpPurpose purpose,
                          LocalDateTime expiresAt, LocalDateTime createdAt) {
        @Override public String toString() { return "NewPasswordOtp[purpose=" + purpose + ", otpHash=[REDACTED]]"; }
    }
}
