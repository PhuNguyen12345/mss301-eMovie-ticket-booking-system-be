package fu.mss301.movie.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import fu.mss301.movie.dto.ImageUploadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CloudinaryServiceTests {
    private Cloudinary cloudinary;
    private Uploader uploader;
    private CloudinaryService service;
    private Map<String, Object> metadata;

    @BeforeEach
    void setUp() throws Exception {
        cloudinary = mock(Cloudinary.class);
        uploader = mock(Uploader.class);
        service = new CloudinaryService(cloudinary);
        when(cloudinary.uploader()).thenReturn(uploader);
        metadata = new HashMap<>(Map.of(
            "secure_url", "https://res.cloudinary.com/demo/image/upload/new.png",
            "format", "png", "bytes", 1000L, "width", 600, "height", 900
        ));
        when(uploader.upload(any(), anyMap())).thenAnswer(invocation -> {
            Map<?, ?> options = invocation.getArgument(1);
            metadata.put("public_id", options.get("public_id"));
            return metadata;
        });
        when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of("result", "ok"));
    }

    @Test
    void urlIsTrimmedAndSentDirectlyToProviderWithUniqueImageOptions() throws Exception {
        ImageUploadResult result = service.uploadImageFromUrl(" https://example.com/poster?id=1 ");
        assertEquals(metadata.get("secure_url"), result.secureUrl());
        assertTrue(result.publicId().startsWith("omega-hub/posters/"));
        verify(uploader).upload(eq("https://example.com/poster?id=1"), argThat(options ->
            "image".equals(options.get("resource_type")) && Boolean.FALSE.equals(options.get("overwrite"))
        ));
        verify(uploader, never()).destroy(anyString(), anyMap());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not a URL", "http://example.com/a.jpg", "file:///tmp/a.jpg",
        "ftp://example.com/a.jpg", "https://user:secret@example.com/a.jpg", "https://localhost/a.jpg",
        "https://127.0.0.1/a.jpg", "https://10.0.0.1/a.jpg", "https://[::1]/a.jpg",
        "https://host.local/a.jpg", "https://example.com:8443/a.jpg", "https://example.com/a.jpg#x"})
    void invalidUrlsNeverCallProvider(String url) {
        assertThrows(IllegalArgumentException.class, () -> service.uploadImageFromUrl(url));
        verifyNoInteractions(cloudinary);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bytes", "pixels", "format"})
    void rejectedRemoteImageIsDeletedBeforeItCanReachDatabase(String failure) throws Exception {
        switch (failure) {
            case "bytes" -> metadata.put("bytes", 5L * 1024 * 1024 + 1);
            case "pixels" -> { metadata.put("width", 10000); metadata.put("height", 10000); }
            case "format" -> metadata.put("format", "gif");
            default -> throw new AssertionError();
        }
        assertThrows(IllegalArgumentException.class,
            () -> service.uploadImageFromUrl("https://example.com/image"));
        verify(uploader).destroy(eq((String) metadata.get("public_id")), anyMap());
    }

    @Test
    void missingMetadataReturnsUpstreamErrorAndCleansAsset() throws Exception {
        metadata.remove("width");
        assertThrows(IOException.class, () -> service.uploadImageFromUrl("https://example.com/image"));
        verify(uploader).destroy(eq((String) metadata.get("public_id")), anyMap());
    }

    @Test
    void cleanupFailureDoesNotReplaceValidationError() throws Exception {
        metadata.put("bytes", 6L * 1024 * 1024);
        when(uploader.destroy(anyString(), anyMap())).thenThrow(new IOException("cleanup failed"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> service.uploadImageFromUrl("https://example.com/image"));
        assertEquals(1, error.getSuppressed().length);
    }

    @Test
    void providerRuntimeErrorIsWrappedWithoutExposingSourceUrl() throws Exception {
        when(uploader.upload(any(), anyMap())).thenThrow(new IllegalArgumentException("provider error with secret URL"));
        IOException error = assertThrows(IOException.class,
            () -> service.uploadImageFromUrl("https://example.com/image"));
        assertEquals("Cloudinary image upload failed", error.getMessage());
    }

    @Test
    void multipartStillUploadsValidatedImageBytes() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), "png", bytes);
        MockMultipartFile file = new MockMultipartFile("file", "poster.png", "image/png", bytes.toByteArray());
        assertNotNull(service.uploadImage(file).secureUrl());
        verify(uploader).upload(any(byte[].class), anyMap());
    }

    @Test
    void invalidFileDoesNotCallProvider() {
        MockMultipartFile file = new MockMultipartFile("file", "poster.jpg", "image/jpeg", new byte[]{1, 2, 3});
        assertThrows(IllegalArgumentException.class, () -> service.uploadImage(file));
        verifyNoInteractions(cloudinary);
    }
}
