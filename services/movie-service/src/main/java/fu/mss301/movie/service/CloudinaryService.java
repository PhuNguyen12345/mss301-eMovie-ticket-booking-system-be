package fu.mss301.movie.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.cloudinary.*;
import com.cloudinary.utils.ObjectUtils;

import fu.mss301.movie.dto.ImageUploadResult;

@Service 
@RequiredArgsConstructor 
@Slf4j
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
            return uploadValidated(bytes);
    }

    public ImageUploadResult uploadImageFromUrl(String imageUrl) throws IOException {
        return uploadValidated(validateImageUrl(imageUrl));
    }

    private String validateImageUrl(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 4096) {
            throw new IllegalArgumentException("Image URL is required and must not exceed 4096 characters");
        }
        try {
            URI uri = new URI(value.trim());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                throw new IllegalArgumentException("Use a public HTTPS image URL without credentials or fragment");
            }
            host = host.toLowerCase(Locale.ROOT);
            // The provider fetches the URL. This application never opens the source URL.
            // Reject IP literals and local hostnames; this is not a redirect/DNS allowlist.
            if (!host.contains(".") || host.matches("[0-9.]+") || host.contains(":")
                    || host.endsWith(".") || host.endsWith(".localhost")
                    || host.endsWith(".local") || host.endsWith(".internal")) {
                throw new IllegalArgumentException("Image URL must use a public domain name");
            }
            return uri.toASCIIString();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid image URL");
        }
    }

    private ImageUploadResult uploadValidated(Object source) throws IOException {
        String publicId = "omega-hub/posters/" + UUID.randomUUID();
        Map<?, ?> result;
        try {
            result = cloudinary.uploader().upload(source, ObjectUtils.asMap(
                "resource_type", "image",
                "public_id", publicId,
                "overwrite", false,
                "allowed_formats", new String[]{"jpg", "jpeg", "png"}
            ));
        } catch (RuntimeException ex) {
            // Keep provider messages (which can contain the source URL) out of API responses.
            throw new IOException("Cloudinary image upload failed", ex);
        }
        try {
            validateUploadedImage(result, publicId);
            return new ImageUploadResult((String) result.get("secure_url"), publicId);
        } catch (IOException | IllegalArgumentException ex) {
            // Remote size/dimensions are known only after import; reject before DB updates.
            try {
                deleteImage(publicId);
            } catch (Exception cleanupFailure) {
                log.warn("Could not clean up rejected image {}", publicId);
                ex.addSuppressed(cleanupFailure);
            }
            throw ex;
        }
    }

    private void validateUploadedImage(Map<?, ?> result, String expectedPublicId) throws IOException {
        if (result == null || !expectedPublicId.equals(result.get("public_id"))
                || !(result.get("secure_url") instanceof String secureUrl)
                || !isHttpsUrl(secureUrl)
                || !(result.get("format") instanceof String format)
                || !(result.get("bytes") instanceof Number bytes)
                || !(result.get("width") instanceof Number width)
                || !(result.get("height") instanceof Number height)
                || bytes.longValue() <= 0 || width.longValue() <= 0 || height.longValue() <= 0) {
            throw new IOException("Cloudinary returned invalid image metadata");
        }
        if (!Set.of("jpg", "jpeg", "png").contains(format.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Only JPEG and PNG are supported");
        }
        if (bytes.longValue() > MAX_BYTES) {
            throw new IllegalArgumentException("Image must not exceed 5 MB");
        }
        if (width.longValue() > MAX_PIXELS / height.longValue()) {
            throw new IllegalArgumentException("Image dimensions are too large");
        }
    }

    private boolean isHttpsUrl(String value) {
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (URISyntaxException ex) {
            return false;
        }
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
