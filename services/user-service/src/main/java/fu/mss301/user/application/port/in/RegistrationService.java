package fu.mss301.user.application.port.in;

import fu.mss301.user.application.dto.RegisterRequest;
import fu.mss301.user.application.dto.ResendRegistrationOtpRequest;
import fu.mss301.user.application.dto.RegistrationOtpResponse;

public interface RegistrationService {
    RegistrationOtpResponse register(RegisterRequest request);
    RegistrationOtpResponse resendOtp(ResendRegistrationOtpRequest request);
}
