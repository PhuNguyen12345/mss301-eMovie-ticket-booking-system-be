package fu.mss301.user.application.port.in;

import fu.mss301.user.application.usecase.dto.ConfirmPasswordChangeRequest;
import fu.mss301.user.application.usecase.dto.PasswordChangeOtpRequest;
import fu.mss301.user.application.usecase.dto.PasswordChangeOtpResponse;
import fu.mss301.user.application.usecase.dto.PasswordChangeResponse;

public interface PasswordChangeService {
    PasswordChangeOtpResponse requestOtp(Integer userId, PasswordChangeOtpRequest request);
    PasswordChangeResponse confirm(Integer userId, ConfirmPasswordChangeRequest request);
}
