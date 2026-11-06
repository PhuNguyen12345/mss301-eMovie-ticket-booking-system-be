package fu.mss301.user.application.port.in;

import fu.mss301.user.application.usecase.dto.RegisterResponse;
import fu.mss301.user.application.usecase.dto.VerifyRegistrationRequest;

public interface RegistrationVerificationService {
    RegisterResponse verify(VerifyRegistrationRequest request);
}
