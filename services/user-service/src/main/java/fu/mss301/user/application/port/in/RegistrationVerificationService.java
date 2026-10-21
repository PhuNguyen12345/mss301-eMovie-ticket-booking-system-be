package fu.mss301.user.application.port.in;

import fu.mss301.user.application.dto.RegisterResponse;
import fu.mss301.user.application.dto.VerifyRegistrationRequest;

public interface RegistrationVerificationService {
    RegisterResponse verify(VerifyRegistrationRequest request);
}
