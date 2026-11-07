package fu.mss301.user.infrastructure.web.controller;

import fu.mss301.common.dto.ApiResponse;
import fu.mss301.user.infrastructure.web.dto.ApiDtoMapper;
import fu.mss301.user.infrastructure.config.GatewayServiceAuthFilter;
import fu.mss301.user.infrastructure.web.dto.ConfirmPasswordChangeRequest;
import fu.mss301.user.infrastructure.web.dto.PasswordChangeOtpRequest;
import fu.mss301.user.infrastructure.web.dto.PasswordChangeOtpResponse;
import fu.mss301.user.infrastructure.web.dto.PasswordChangeResponse;
import fu.mss301.user.application.port.in.PasswordChangeService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/users/me/password", "/api/v1/users/me/password"})
@SecurityRequirement(name = "bearerAuth")
public class PasswordChangeController {
    private final PasswordChangeService passwords;

    public PasswordChangeController(PasswordChangeService passwords) {
        this.passwords = passwords;
    }

    @PostMapping("/otp")
    public ResponseEntity<ApiResponse<PasswordChangeOtpResponse>> requestOtp(
            @RequestAttribute(GatewayServiceAuthFilter.USER_ID_ATTRIBUTE) Integer userId,
            @Valid @RequestBody PasswordChangeOtpRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ApiResponse<>(HttpStatus.ACCEPTED.value(), "Password change OTP requested",
                        ApiDtoMapper.toApi(passwords.requestOtp(userId, ApiDtoMapper.toApplication(request)))));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<PasswordChangeResponse>> confirm(
            @RequestAttribute(GatewayServiceAuthFilter.USER_ID_ATTRIBUTE) Integer userId,
            @Valid @RequestBody ConfirmPasswordChangeRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Password changed",
                ApiDtoMapper.toApi(passwords.confirm(userId, ApiDtoMapper.toApplication(request)))));
    }
}
