package fu.mss301.user.application.service;

import fu.mss301.user.application.port.in.RefreshTokenService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import fu.mss301.common.exception.ApiException;
import fu.mss301.user.domain.model.RefreshCredential;
import fu.mss301.user.domain.model.UserRole;
import fu.mss301.user.domain.model.RoleName;
import fu.mss301.user.domain.model.User;
import fu.mss301.user.application.port.out.RefreshTokenStore;
import fu.mss301.user.application.port.out.UserStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RefreshTokenServiceTest {
    private final RefreshTokenStore tokens = mock(RefreshTokenStore.class);
    private final UserStore users = mock(UserStore.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T07:00:00Z"), ZoneOffset.UTC);
    private final RefreshTokenService service = new RefreshTokenUseCase(tokens, users, clock, Duration.ofDays(30));
    private final User user = User.builder().id(1).email("admin@gmail.com").fullName("Admin")
            .status("ACTIVE").emailVerifiedAt(LocalDateTime.of(2026, 10, 1, 0, 0))
            .roles(Set.of(new UserRole(1, RoleName.ADMIN))).build();

    @Test
    void issueStoresOnlyHashAndExpiry() throws Exception {
        when(users.getReferenceById(1)).thenReturn(user);
        var issued = service.issue(1);
        var saved = captureSaved();
        assertEquals(43, issued.token().length());
        assertEquals(2592000, issued.expiresIn());
        assertEquals(sha256(issued.token()), saved.tokenHash());
        assertNotEquals(issued.token(), saved.tokenHash());
        assertEquals(LocalDateTime.of(2026, 11, 11, 7, 0), saved.expiresAt());
        assertFalse(saved.revoked());
        assertFalse(saved.toString().contains(issued.token()));
        assertFalse(saved.toString().contains(saved.tokenHash()));
    }

    @Test
    void rotateRevokesOldTokenAndIssuesNewOne() throws Exception {
        when(users.getReferenceById(1)).thenReturn(user);
        String original = service.issue(1).token();
        clearInvocations(tokens);
        var old = RefreshCredential.builder().user(user)
                .expiresAt(LocalDateTime.of(2026, 10, 13, 7, 0)).revoked(false).build();
        when(tokens.findByTokenHash(sha256(original))).thenReturn(Optional.of(old));

        var rotated = service.rotate(original);

        assertTrue(old.getRevoked());
        assertEquals(LocalDateTime.of(2026, 10, 12, 7, 0), old.getRevokedAt());
        verify(tokens).update(old);
        assertEquals(Set.of(RoleName.ADMIN), rotated.user().roles());
        assertNotEquals(original, rotated.refreshToken().token());
        assertEquals(sha256(rotated.refreshToken().token()), captureSaved().tokenHash());
    }

    @Test
    void rejectedTokenDoesNotIssueReplacement() throws Exception {
        when(users.getReferenceById(1)).thenReturn(user);
        String original = service.issue(1).token();
        clearInvocations(tokens);
        var old = RefreshCredential.builder().user(user)
                .expiresAt(LocalDateTime.of(2026, 10, 13, 7, 0)).revoked(true).build();
        when(tokens.findByTokenHash(sha256(original))).thenReturn(Optional.of(old));
        assertThrows(ApiException.class, () -> service.rotate(original));
        verify(tokens, never()).update(any());
        verify(tokens, never()).saveNew(any());
    }

    @Test
    void expiredTokenDoesNotIssueReplacement() throws Exception {
        when(users.getReferenceById(1)).thenReturn(user);
        String original = service.issue(1).token();
        clearInvocations(tokens);
        var old = RefreshCredential.builder().user(user)
                .expiresAt(LocalDateTime.of(2026, 10, 12, 7, 0)).revoked(false).build();
        when(tokens.findByTokenHash(sha256(original))).thenReturn(Optional.of(old));
        assertThrows(ApiException.class, () -> service.rotate(original));
        verify(tokens, never()).saveNew(any());
    }

    @Test
    void inactiveAccountCannotRefresh() throws Exception {
        when(users.getReferenceById(1)).thenReturn(user);
        String original = service.issue(1).token();
        clearInvocations(tokens);
        user.setStatus("SUSPENDED");
        var old = RefreshCredential.builder().user(user)
                .expiresAt(LocalDateTime.of(2026, 10, 13, 7, 0)).revoked(false).build();
        when(tokens.findByTokenHash(sha256(original))).thenReturn(Optional.of(old));
        assertThrows(ApiException.class, () -> service.rotate(original));
        assertFalse(old.getRevoked());
        verify(tokens, never()).saveNew(any());
    }

    private RefreshTokenStore.NewRefreshToken captureSaved() {
        var captor = ArgumentCaptor.forClass(RefreshTokenStore.NewRefreshToken.class);
        verify(tokens).saveNew(captor.capture());
        return captor.getValue();
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.US_ASCII)));
    }
}
