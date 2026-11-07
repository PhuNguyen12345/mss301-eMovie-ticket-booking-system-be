package fu.mss301.user.application.port.in;

import fu.mss301.user.application.usecase.dto.ForgotPasswordRequest;
import fu.mss301.user.application.usecase.dto.PasswordChangeResponse;
import fu.mss301.user.application.usecase.dto.ResetPasswordRequest;

public interface PasswordResetService {
    void requestOtp(ForgotPasswordRequest request);
    PasswordChangeResponse reset(ResetPasswordRequest request);
}
