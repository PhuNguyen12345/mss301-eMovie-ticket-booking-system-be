package fu.mss301.user.infrastructure.web.dto;

/** Maps HTTP records to and from application data without exposing HTTP types to use cases. */
public final class ApiDtoMapper {
    private ApiDtoMapper() { }

    public static fu.mss301.user.application.usecase.dto.LoginRequest toApplication(LoginRequest value) {
        return new fu.mss301.user.application.usecase.dto.LoginRequest(value.email(), value.password());
    }

    public static fu.mss301.user.application.usecase.dto.RefreshRequest toApplication(RefreshRequest value) {
        return new fu.mss301.user.application.usecase.dto.RefreshRequest(value.refreshToken());
    }

    public static fu.mss301.user.application.usecase.dto.RegisterRequest toApplication(RegisterRequest value) {
        return new fu.mss301.user.application.usecase.dto.RegisterRequest(value.fullName(), value.email(), value.phone(), value.password());
    }

    public static fu.mss301.user.application.usecase.dto.ResendRegistrationOtpRequest toApplication(ResendRegistrationOtpRequest value) {
        return new fu.mss301.user.application.usecase.dto.ResendRegistrationOtpRequest(value.registrationId());
    }

    public static fu.mss301.user.application.usecase.dto.VerifyRegistrationRequest toApplication(VerifyRegistrationRequest value) {
        return new fu.mss301.user.application.usecase.dto.VerifyRegistrationRequest(value.registrationId(), value.otp());
    }

    public static fu.mss301.user.application.usecase.dto.ForgotPasswordRequest toApplication(ForgotPasswordRequest value) {
        return new fu.mss301.user.application.usecase.dto.ForgotPasswordRequest(value.email());
    }

    public static fu.mss301.user.application.usecase.dto.ResetPasswordRequest toApplication(ResetPasswordRequest value) {
        return new fu.mss301.user.application.usecase.dto.ResetPasswordRequest(value.email(), value.otp(), value.newPassword());
    }

    public static fu.mss301.user.application.usecase.dto.PasswordChangeOtpRequest toApplication(PasswordChangeOtpRequest value) {
        return new fu.mss301.user.application.usecase.dto.PasswordChangeOtpRequest(value.currentPassword());
    }

    public static fu.mss301.user.application.usecase.dto.ConfirmPasswordChangeRequest toApplication(ConfirmPasswordChangeRequest value) {
        return new fu.mss301.user.application.usecase.dto.ConfirmPasswordChangeRequest(value.otp(), value.newPassword());
    }

    public static LoginResponse toApi(fu.mss301.user.application.usecase.dto.LoginResponse value) {
        var user = value.user();
        return new LoginResponse(value.accessToken(), value.tokenType(), value.expiresIn(),
                value.refreshToken(), value.refreshExpiresIn(),
                new LoginResponse.LoginUserResponse(user.userId(), user.fullName(), user.email(), user.roles()));
    }

    public static RegistrationOtpResponse toApi(fu.mss301.user.application.usecase.dto.RegistrationOtpResponse value) {
        return new RegistrationOtpResponse(value.registrationId(), value.expiresAt(),
                value.resendAvailableAt(), value.otpDeliveryStatus());
    }

    public static RegisterResponse toApi(fu.mss301.user.application.usecase.dto.RegisterResponse value) {
        return new RegisterResponse(value.userId(), value.fullName(), value.email(), value.phone(), value.status());
    }

    public static PasswordChangeOtpResponse toApi(fu.mss301.user.application.usecase.dto.PasswordChangeOtpResponse value) {
        return new PasswordChangeOtpResponse(value.expiresAt(), value.resendAvailableAt(), value.otpDeliveryStatus());
    }

    public static PasswordChangeResponse toApi(fu.mss301.user.application.usecase.dto.PasswordChangeResponse value) {
        return new PasswordChangeResponse(value.changedAt());
    }

    public static UserProfileResponse toApi(fu.mss301.user.application.usecase.dto.UserProfileResponse value) {
        return new UserProfileResponse(value.userId(), value.fullName(), value.email(), value.phone(),
                value.dateOfBirth(), value.gender(), value.cityProvince(), value.district(), value.address(),
                value.preferredCinemaId(), value.avatarUrl());
    }
}
