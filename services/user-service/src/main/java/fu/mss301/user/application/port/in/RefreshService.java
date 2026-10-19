package fu.mss301.user.application.port.in;

import fu.mss301.user.application.dto.LoginResponse;
import fu.mss301.user.application.dto.RefreshRequest;

public interface RefreshService {
    LoginResponse refresh(RefreshRequest request);
}
