package fu.mss301.user.application.event;

import java.time.Instant;

/** In-memory event; its default toString does not expose the OTP. */
public final class PasswordChangeOtpRequested {
    private final Integer otpId;
    private final String email;
    private final String otp;
    private final String otpHash;
    private final Instant expiresAt;

    public PasswordChangeOtpRequested(Integer otpId, String email, String otp,
                                      String otpHash, Instant expiresAt) {
        this.otpId = otpId;
        this.email = email;
        this.otp = otp;
        this.otpHash = otpHash;
        this.expiresAt = expiresAt;
    }

    public Integer otpId() { return otpId; }
    public String email() { return email; }
    public String otp() { return otp; }
    public String otpHash() { return otpHash; }
    public Instant expiresAt() { return expiresAt; }
}
