package fu.mss301.booking.infrastructure.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import fu.mss301.booking.application.port.QrCodeRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/** Draws QR codes with ZXing and writes them as black and white PNG. */
@Component
class ZxingQrCodeRenderer implements QrCodeRenderer {
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    @Override
    public byte[] png(String content, int size) {
        try {
            var matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size,
                Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
            var image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
            for (int x = 0; x < matrix.getWidth(); x++) {
                for (int y = 0; y < matrix.getHeight(); y++) image.setRGB(x, y, matrix.get(x, y) ? BLACK : WHITE);
            }
            var out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (WriterException exception) {
            throw new IllegalArgumentException("Cannot draw a QR code for this content", exception);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
