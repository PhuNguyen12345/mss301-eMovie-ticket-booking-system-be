package fu.mss301.user.presentation.security;

import fu.mss301.user.infrastructure.persistence.entity.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class GatewayServiceAuthFilterTest {
    private static final String SECRET = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
    private final GatewayServiceAuthFilter filter = new GatewayServiceAuthFilter(SECRET);

    @Test
    void directRequestWithForgedIdentityIsRejected() throws Exception {
        var request = request("GET", "/api/v1/users/me");
        request.addHeader("X-User-Id", "42");
        request.addHeader("X-User-Roles", "ADMIN");
        assertUnauthorized(request);

        request.addHeader("X-Gateway-Key", "wrong");
        assertUnauthorized(request);
    }

    @Test
    void publicAuthEndpointAlsoRequiresGatewayKey() throws Exception {
        assertUnauthorized(request("POST", "/api/v1/auth/login"));
        var request = request("POST", "/api/v1/auth/login");
        request.addHeader("X-Gateway-Key", SECRET);
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void gatewayIdentityIsAvailableToController() throws Exception {
        var request = request("GET", "/api/v1/users/me");
        request.addHeader("X-Gateway-Key", SECRET);
        request.addHeader("X-User-Id", "42");
        request.addHeader("X-User-Roles", "ADMIN,CUSTOMER");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(42, request.getAttribute(GatewayServiceAuthFilter.USER_ID_ATTRIBUTE));
        assertEquals(Set.of("ADMIN", "CUSTOMER"),
                request.getAttribute(GatewayServiceAuthFilter.USER_ROLES_ATTRIBUTE));
        verify(chain).doFilter(request, response);
    }

    @Test
    void missingOrDuplicateIdentityIsRejected() throws Exception {
        var missing = request("GET", "/api/v1/users/me");
        missing.addHeader("X-Gateway-Key", SECRET);
        assertUnauthorized(missing);

        var duplicate = request("GET", "/api/v1/users/me");
        duplicate.addHeader("X-Gateway-Key", SECRET);
        duplicate.addHeader("X-User-Id", "42");
        duplicate.addHeader("X-User-Id", "99");
        duplicate.addHeader("X-User-Roles", "CUSTOMER");
        assertUnauthorized(duplicate);
    }

    @Test
    void shortSharedKeyIsRejectedAtStartup() {
        assertThrows(IllegalArgumentException.class,
                () -> new GatewayServiceAuthFilter(Base64.getEncoder().encodeToString(new byte[16])));
    }

    private void assertUnauthorized(MockHttpServletRequest request) throws Exception {
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain);
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }
}
