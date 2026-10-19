package fu.mss301.user.application.port.in;

import fu.mss301.user.application.dto.ConfirmPasswordChangeRequest;
import fu.mss301.user.application.dto.PasswordChangeOtpRequest;
import fu.mss301.user.application.dto.PasswordChangeOtpResponse;
import fu.mss301.user.application.dto.PasswordChangeResponse;

public interface PasswordChangeService {
    PasswordChangeOtpResponse requestOtp(Integer userId, PasswordChangeOtpRequest request);
    PasswordChangeResponse confirm(Integer userId, ConfirmPasswordChangeRequest request);
}
