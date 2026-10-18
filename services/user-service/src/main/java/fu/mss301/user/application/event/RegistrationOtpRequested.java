package fu.mss301.user.application.event;

import java.time.Instant;

/** In-memory event with no toString that could expose the OTP. */
public final class RegistrationOtpRequested {
    private final String registrationId;
    private final String email;
    private final String otp;
    private final String otpHash;
    private final Instant expiresAt;

    public RegistrationOtpRequested(String registrationId, String email, String otp,
                                    String otpHash, Instant expiresAt) {
        this.registrationId = registrationId;
        this.email = email;
        this.otp = otp;
        this.otpHash = otpHash;
        this.expiresAt = expiresAt;
    }

    public String registrationId() { return registrationId; }
    public String email() { return email; }
    public String otp() { return otp; }
    public String otpHash() { return otpHash; }
    public Instant expiresAt() { return expiresAt; }
}
