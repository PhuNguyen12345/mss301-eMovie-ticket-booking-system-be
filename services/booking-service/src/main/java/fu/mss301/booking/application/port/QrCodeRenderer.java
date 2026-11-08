package fu.mss301.booking.application.port;

/** Draws a QR code. */
public interface QrCodeRenderer {
    /** @return a PNG image {@code size} pixels wide and high */
    byte[] png(String content, int size);
}
