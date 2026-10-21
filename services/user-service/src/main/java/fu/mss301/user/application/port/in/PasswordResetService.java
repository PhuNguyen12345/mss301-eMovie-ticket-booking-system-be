package fu.mss301.user.application.port.in;

import fu.mss301.user.application.dto.ForgotPasswordRequest;
import fu.mss301.user.application.dto.PasswordChangeResponse;
import fu.mss301.user.application.dto.ResetPasswordRequest;

public interface PasswordResetService {
    void requestOtp(ForgotPasswordRequest request);
    PasswordChangeResponse reset(ResetPasswordRequest request);
}
