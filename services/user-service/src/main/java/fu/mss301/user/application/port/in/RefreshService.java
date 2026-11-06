package fu.mss301.user.application.port.in;

import fu.mss301.user.application.usecase.dto.LoginResponse;
import fu.mss301.user.application.usecase.dto.RefreshRequest;

public interface RefreshService {
    LoginResponse refresh(RefreshRequest request);
}
