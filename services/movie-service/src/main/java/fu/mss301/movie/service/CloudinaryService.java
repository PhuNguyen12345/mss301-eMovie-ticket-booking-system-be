package fu.mss301.movie.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import com.cloudinary.*;
import com.cloudinary.utils.ObjectUtils;

import fu.mss301.movie.dto.ImageUploadResult;

@Service 
@RequiredArgsConstructor 
public class CloudinaryService {
    //5MB = 5 * 1024 * 1024 B 
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    //25 mpx  
    private static final long MAX_PIXELS = 25_000_000; 
    private static final Set<String> FORMATS = Set.of("jpeg", "png");  
    
    private final Cloudinary cloudinary; 

    public ImageUploadResult uploadImage (MultipartFile file) 
        throws IOException {

            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException(
                    "Image file is required."
                ); 
            }

            if (file.getSize() > MAX_BYTES) {
                throw new IllegalArgumentException(
                    "Image must not exceed 5MB"
                ); 
            }

            byte[] bytes = file.getBytes(); 
            validateImage(bytes); 
            
            Map<?, ?> result = cloudinary.uploader().upload(
                bytes, 
                ObjectUtils.asMap(
                    "resource_type", "image", 
                    "public_id", "omega-hub/posters/" + UUID.randomUUID(), 
                    "overwrite", false, 
                    "allowed_formats", new String[]{"jpg", "jpeg", "png"}
                )
            ); 
        return new ImageUploadResult(
            (String) result.get("secure_url"), 
            (String) result.get("public_id")
        ); 
    }

    public void deleteImage (String publicId) throws IOException {
        if (publicId == null || publicId.isBlank()) {
            return; 
        }

        Map<?,?> result = cloudinary.uploader().destroy(
            publicId, 
            ObjectUtils.asMap(
                "resource_type", "image", 
                "invalidate", true
            )
        ); 

        String status = (String) result.get("result"); 

        if (!"ok".equals(status) && !"not found".equals(status)) {
            throw new IOException("Cloudinary could not delete the image"); 
        }
    }

    private void validateImage(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(
            new ByteArrayInputStream(bytes)
        )) {
            if (input == null) {
                throw new IllegalArgumentException(
                    "Invalid image"
                ); 
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input); 

            if (!readers.hasNext()) {
                throw new IllegalArgumentException(
                    "Only JPEG and PNG are supported"
                ); 
            }

            ImageReader reader = readers.next(); 
            try {
                reader.setInput(input); 

                String format = reader.getFormatName().toLowerCase(
                    java.util.Locale.ROOT
                ); 

                if (!FORMATS.contains(format)) {
                    throw new IllegalArgumentException(
                        "Only JPEG and PNG are supported."
                    ); 
                }

                long pixels = (long) reader.getWidth(0) * reader.getHeight(0); 

                if (pixels > MAX_PIXELS) {
                    throw new IllegalArgumentException(
                        "Image dimension are too large"
                    ); 
                }
            } finally {
                reader.dispose(); 
            }
        }
    }
}
