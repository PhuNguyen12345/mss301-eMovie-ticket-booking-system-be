package fu.mss301.user.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class GatewayServiceAuthFilterTest {
    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private final GatewayServiceAuthFilter filter = new GatewayServiceAuthFilter(SECRET);

    @Test
    void forgedIdentityAndUntrustedGatewayKeysAreRejectedOnBothAliases() throws Exception {
        for (String path : new String[]{"/api/users/me", "/api/v1/users/me", "/api/auth/login", "/api/v1/auth/login"}) {
            var request = new MockHttpServletRequest("POST", path);
            request.addHeader("X-User-Id", "42");
            request.addHeader("X-User-Roles", "ADMIN");
            assertUnauthorized(request);
            request.addHeader("X-Gateway-Key", "wrong");
            assertUnauthorized(request);
            request.removeHeader("X-Gateway-Key");
            request.addHeader("X-Gateway-Key", SECRET);
            request.addHeader("X-Gateway-Key", SECRET);
            assertUnauthorized(request);
        }
    }

    @Test
    void invalidConfigurationFailsClosed() {
        for (String secret : new String[]{"", "not-base64!", "c2hvcnQ="}) {
            assertThrows(IllegalArgumentException.class, () -> new GatewayServiceAuthFilter(secret));
        }
    }

    @Test
    void missingIdentityIsRejectedOnBothUserPaths() throws Exception {
        assertUnauthorized(request("GET", "/api/users/me"));
        assertUnauthorized(request("GET", "/api/v1/users/me"));

        var missingRoles = request("GET", "/api/users/me");
        missingRoles.addHeader("X-User-Id", "42");
        assertUnauthorized(missingRoles);
    }

    @Test
    void invalidOrDuplicateIdentityIsRejected() throws Exception {
        var invalid = request("GET", "/api/users/me");
        invalid.addHeader("X-User-Id", "not-a-number");
        invalid.addHeader("X-User-Roles", "ADMIN");
        assertUnauthorized(invalid);

        var duplicate = request("GET", "/api/users/me");
        duplicate.addHeader("X-User-Id", "42");
        duplicate.addHeader("X-User-Id", "99");
        duplicate.addHeader("X-User-Roles", "CUSTOMER");
        assertUnauthorized(duplicate);
    }

    @Test
    void publicAuthEndpointPassesWithGatewayKeyWithoutIdentity() throws Exception {
        var request = request("POST", "/api/auth/login");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void gatewayIdentityIsAvailableToControllerWithGatewayKey() throws Exception {
        var request = request("GET", "/api/users/me");
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

    private void assertUnauthorized(MockHttpServletRequest request) throws Exception {
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain);
    }

    private MockHttpServletRequest request(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.addHeader("X-Gateway-Key", SECRET);
        return request;
    }
}
