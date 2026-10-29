package fu.mss301.booking.presentation.security;

import fu.mss301.common.exception.ApiException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Arrays;
import java.util.stream.Collectors;

public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {
    static final String USER_ID = "X-User-Id";
    static final String USER_ROLES = "X-User-Roles";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
            && parameter.getParameterType().equals(AuthenticatedUser.class);
    }

    @Override
    public AuthenticatedUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                             NativeWebRequest request, WebDataBinderFactory binderFactory) {
        String userId = request.getHeader(USER_ID);
        if (userId == null || !userId.matches("[1-9][0-9]{0,9}")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String roles = request.getHeader(USER_ROLES);
        try {
            return new AuthenticatedUser(Integer.valueOf(userId), roles == null || roles.isBlank() ? java.util.Set.of()
                : Arrays.stream(roles.split(",")).map(String::trim).filter(r -> !r.isEmpty())
                    .collect(Collectors.toUnmodifiableSet()));
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid user id");
        }
    }
}
