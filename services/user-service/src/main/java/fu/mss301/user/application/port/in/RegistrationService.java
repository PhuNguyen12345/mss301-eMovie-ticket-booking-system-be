package fu.mss301.user.application.port.in;

import fu.mss301.user.application.usecase.dto.RegisterRequest;
import fu.mss301.user.application.usecase.dto.ResendRegistrationOtpRequest;
import fu.mss301.user.application.usecase.dto.RegistrationOtpResponse;

public interface RegistrationService {
    RegistrationOtpResponse register(RegisterRequest request);
    RegistrationOtpResponse resendOtp(ResendRegistrationOtpRequest request);
}
