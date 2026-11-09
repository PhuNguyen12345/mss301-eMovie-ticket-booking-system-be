package fu.mss301.movie.application.port.out;

import fu.mss301.movie.application.dto.ImageUploadResult;
import java.io.IOException;
public interface ImageStorage {
    ImageUploadResult uploadImage(byte[] bytes) throws IOException;
    ImageUploadResult uploadImageFromUrl(String url) throws IOException;
    void deleteImage(String publicId) throws IOException;
}
