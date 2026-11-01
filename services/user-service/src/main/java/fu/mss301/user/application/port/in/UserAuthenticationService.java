package fu.mss301.user.application.port.in;

import fu.mss301.user.application.usecase.dto.LoginRequest;
import fu.mss301.user.application.usecase.dto.LoginResponse.LoginUserResponse;

public interface UserAuthenticationService {
    LoginUserResponse authenticate(LoginRequest request);
}
