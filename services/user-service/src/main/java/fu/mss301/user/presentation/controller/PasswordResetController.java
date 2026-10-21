package fu.mss301.user.presentation.controller;

import fu.mss301.common.dto.ApiResponse;
import fu.mss301.user.presentation.dto.ApiDtoMapper;
import fu.mss301.user.presentation.dto.ForgotPasswordRequest;
import fu.mss301.user.presentation.dto.PasswordChangeResponse;
import fu.mss301.user.presentation.dto.ResetPasswordRequest;
import fu.mss301.user.application.port.in.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/password")
public class PasswordResetController {
    private final PasswordResetService passwords;

    public PasswordResetController(PasswordResetService passwords) {
        this.passwords = passwords;
    }

    @PostMapping("/forgot")
    public ResponseEntity<ApiResponse<Void>> forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        passwords.requestOtp(ApiDtoMapper.toApplication(request));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ApiResponse<>(HttpStatus.ACCEPTED.value(),
                        "If the account exists, a password reset OTP will be sent", null));
    }

    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<PasswordChangeResponse>> reset(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Password reset successful",
                ApiDtoMapper.toApi(passwords.reset(ApiDtoMapper.toApplication(request)))));
    }
}
