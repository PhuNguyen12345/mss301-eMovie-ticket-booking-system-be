package fu.mss301.user.presentation.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Enumeration;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class GatewayServiceAuthFilter extends OncePerRequestFilter {
    public static final String USER_ID_ATTRIBUTE = "gatewayUserId";
    public static final String USER_ROLES_ATTRIBUTE = "gatewayUserRoles";
    private static final String GATEWAY_KEY = "X-Gateway-Key";
    private static final String USER_ID = "X-User-Id";
    private static final String USER_ROLES = "X-User-Roles";
    private final byte[] expectedKey;

    public GatewayServiceAuthFilter(@Value("${gateway.shared-secret}") String sharedSecret) {
        try {
            if (Base64.getDecoder().decode(sharedSecret).length < 32) {
                throw new IllegalArgumentException("Gateway shared key must contain at least 32 random bytes");
            }
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalArgumentException("GATEWAY_SHARED_SECRET must be a base64-encoded 32-byte key", ex);
        }
        this.expectedKey = sharedSecret.getBytes(StandardCharsets.US_ASCII);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.equals("/api/v1") && !path.startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
//        String suppliedKey = singleHeader(request, GATEWAY_KEY);
//        if (suppliedKey == null || !MessageDigest.isEqual(expectedKey,
//                suppliedKey.getBytes(StandardCharsets.US_ASCII))) {
//            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Gateway authentication required");
//            return;
//        }

        String path = request.getRequestURI();
        if (path.equals("/api/v1/users") || path.startsWith("/api/v1/users/")) {
            String userIdHeader = singleHeader(request, USER_ID);
            String rolesHeader = singleHeader(request, USER_ROLES);
            if (userIdHeader == null || !userIdHeader.matches("[1-9][0-9]*")
                    || rolesHeader == null || !rolesHeader.matches("[A-Z_]+(?:,[A-Z_]+)*")) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Gateway identity required");
                return;
            }
            try {
                request.setAttribute(USER_ID_ATTRIBUTE, Integer.valueOf(userIdHeader));
            } catch (NumberFormatException exception) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid user ID");
                return;
            }
            Set<String> roles = Arrays.stream(rolesHeader.split(",")).collect(Collectors.toUnmodifiableSet());
            request.setAttribute(USER_ROLES_ATTRIBUTE, roles);
        }
        chain.doFilter(request, response);
    }

    private String singleHeader(HttpServletRequest request, String name) {
        Enumeration<String> values = request.getHeaders(name);
        if (values == null || !values.hasMoreElements()) {
            return null;
        }
        String value = values.nextElement();
        return values.hasMoreElements() ? null : value;
    }
}
