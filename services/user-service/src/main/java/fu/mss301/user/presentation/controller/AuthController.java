package fu.mss301.user.presentation.controller;

import fu.mss301.common.dto.ApiResponse;
import fu.mss301.user.presentation.dto.ApiDtoMapper;
import fu.mss301.user.presentation.dto.LoginRequest;
import fu.mss301.user.presentation.dto.LoginResponse;
import fu.mss301.user.presentation.dto.RefreshRequest;
import fu.mss301.user.application.port.in.LoginService;
import fu.mss301.user.application.port.in.RefreshService;
import fu.mss301.user.presentation.dto.RegisterRequest;
import fu.mss301.user.presentation.dto.RegistrationOtpResponse;
import fu.mss301.user.presentation.dto.RegisterResponse;
import fu.mss301.user.presentation.dto.ResendRegistrationOtpRequest;
import fu.mss301.user.presentation.dto.VerifyRegistrationRequest;
import fu.mss301.user.application.port.in.RegistrationService;
import fu.mss301.user.application.port.in.RegistrationVerificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final RegistrationVerificationService registrationVerificationService;
    private final LoginService loginService;
    private final RefreshService refreshService;

    public AuthController(LoginService loginService,
                          RefreshService refreshService, RegistrationService registrationService,
                          RegistrationVerificationService registrationVerificationService) {
        this.loginService = loginService;
        this.refreshService = refreshService;
        this.registrationService = registrationService;
        this.registrationVerificationService = registrationVerificationService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Login successful", ApiDtoMapper.toApi(loginService.login(ApiDtoMapper.toApplication(request)))));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Token refreshed",
                ApiDtoMapper.toApi(refreshService.refresh(ApiDtoMapper.toApplication(request)))));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegistrationOtpResponse>> register(@Valid @RequestBody RegisterRequest request) {
        RegistrationOtpResponse result = ApiDtoMapper.toApi(registrationService.register(ApiDtoMapper.toApplication(request)));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ApiResponse<>(HttpStatus.ACCEPTED.value(),
                        "Registration received; OTP delivery is pending", result));
    }

    @PostMapping("/register/verify")
    public ResponseEntity<ApiResponse<RegisterResponse>> verifyRegistration(
            @Valid @RequestBody VerifyRegistrationRequest request) {
        RegisterResponse result = ApiDtoMapper.toApi(registrationVerificationService.verify(ApiDtoMapper.toApplication(request)));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(HttpStatus.CREATED.value(), "Registration confirmed", result));
    }

    @PostMapping("/register/resend-otp")
    public ResponseEntity<ApiResponse<RegistrationOtpResponse>> resendRegistrationOtp(
            @Valid @RequestBody ResendRegistrationOtpRequest request) {
        RegistrationOtpResponse result = ApiDtoMapper.toApi(registrationService.resendOtp(ApiDtoMapper.toApplication(request)));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ApiResponse<>(HttpStatus.ACCEPTED.value(), "OTP resend requested", result));
    }
}
