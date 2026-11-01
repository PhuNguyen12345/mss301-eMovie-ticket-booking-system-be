package fu.mss301.user.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class GatewayServiceAuthFilterTest {
    private final GatewayServiceAuthFilter filter = new GatewayServiceAuthFilter();

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
    void publicAuthEndpointPassesWithoutIdentity() throws Exception {
        var request = request("POST", "/api/auth/login");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void gatewayIdentityIsAvailableToControllerWithoutGatewayKey() throws Exception {
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
        return new MockHttpServletRequest(method, path);
    }
}
