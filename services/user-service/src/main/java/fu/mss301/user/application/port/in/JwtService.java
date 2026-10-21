package fu.mss301.user.application.port.in;

import fu.mss301.user.application.dto.LoginResponse;
import fu.mss301.user.application.dto.LoginResponse.LoginUserResponse;

public interface JwtService {
    LoginResponse issue(LoginUserResponse user);
}
