package fu.mss301.user.application.port.in;

import fu.mss301.user.application.dto.LoginRequest;
import fu.mss301.user.application.dto.LoginResponse;

public interface LoginService {
    LoginResponse login(LoginRequest request);
}
