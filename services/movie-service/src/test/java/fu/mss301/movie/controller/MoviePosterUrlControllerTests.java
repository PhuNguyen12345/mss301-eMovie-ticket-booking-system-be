package fu.mss301.movie.controller;

import fu.mss301.movie.dto.ImageUploadResult;
import fu.mss301.movie.dto.MoviePosterResponseDTO;
import fu.mss301.movie.exception.MovieExceptionHandler;
import fu.mss301.movie.exception.MovieNotFoundException;
import fu.mss301.movie.repository.MovieRepository;
import fu.mss301.movie.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionSystemException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MoviePosterUrlControllerTests {
    private MovieRepository movies;
    private CloudinaryService storage;
    private MoviePosterWriter writer;
    private MoviePosterService service;
    private MockMvc mvc;
    private final ImageUploadResult uploaded = new ImageUploadResult("https://res.cloudinary.com/new.jpg", "new-id");

    @BeforeEach
    void setUp() throws Exception {
        movies = mock(MovieRepository.class);
        storage = mock(CloudinaryService.class);
        writer = mock(MoviePosterWriter.class);
        service = new MoviePosterService(movies, storage, writer);
        mvc = MockMvcBuilders.standaloneSetup(new MovieController(mock(MovieService.class), service))
            .setControllerAdvice(new MovieExceptionHandler()).build();
        when(movies.existsById(10)).thenReturn(true);
        when(storage.uploadImageFromUrl("https://example.com/poster.jpg")).thenReturn(uploaded);
        when(writer.replace(10, uploaded)).thenReturn(new MoviePosterWriter.PosterReplacement(
            new MoviePosterResponseDTO(10, uploaded.secureUrl()), "old-id"
        ));
    }

    @Test
    void importsAndCommitsBeforeDeletingOldPoster() throws Exception {
        request().andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.movieId").value(10))
            .andExpect(jsonPath("$.data.posterUrl").value(uploaded.secureUrl()));
        var order = inOrder(storage, writer);
        order.verify(storage).uploadImageFromUrl("https://example.com/poster.jpg");
        order.verify(writer).replace(10, uploaded);
        order.verify(storage).deleteImage("old-id");
        verify(storage, never()).deleteImage("new-id");
    }

    @Test
    void noPreviousPosterNeedsNoDeletion() throws Exception {
        when(writer.replace(10, uploaded)).thenReturn(new MoviePosterWriter.PosterReplacement(
            new MoviePosterResponseDTO(10, uploaded.secureUrl()), null
        ));
        request().andExpect(status().isOk());
        verify(storage, never()).deleteImage(any());
    }

    @Test
    void uploadFailurePreservesDatabaseAndOldPoster() throws Exception {
        when(storage.uploadImageFromUrl(anyString())).thenThrow(new IOException("source unavailable"));
        request().andExpect(status().isBadGateway()).andExpect(jsonPath("$.status").value(502));
        verifyNoInteractions(writer);
        verify(storage, never()).deleteImage(anyString());
    }

    @Test
    void rejectedInputNeverUpdatesDatabase() throws Exception {
        when(storage.uploadImageFromUrl(anyString())).thenThrow(new IllegalArgumentException("Invalid image URL"));
        request().andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(writer);
    }

    @Test
    void missingMovieOrInvalidIdNeverUploads() throws Exception {
        mvc.perform(put("/api/movies/99/poster/from-url").contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://example.com/poster.jpg\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(put("/api/movies/0/poster/from-url").contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://example.com/poster.jpg\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(storage, writer);
    }

    @Test
    void rollbackCleansNewPosterAndKeepsOldOne() throws Exception {
        when(writer.replace(10, uploaded)).thenThrow(new MovieNotFoundException(10));
        request().andExpect(status().isNotFound());
        verify(storage).deleteImage("new-id");
        verify(storage, never()).deleteImage("old-id");
    }

    @Test
    void databaseConstraintFailureAlsoCleansNewPoster() throws Exception {
        when(writer.replace(10, uploaded)).thenThrow(new DataIntegrityViolationException("constraint"));
        var dto = new fu.mss301.movie.dto.MoviePosterUrlRequestDTO();
        dto.setUrl("https://example.com/poster.jpg");
        assertThrows(DataIntegrityViolationException.class, () -> service.uploadPosterFromUrl(10, dto));
        verify(storage).deleteImage("new-id");
        verify(storage, never()).deleteImage("old-id");
    }

    @Test
    void uncertainCommitKeepsBothAssetsForReconciliation() throws Exception {
        when(writer.replace(10, uploaded)).thenThrow(new TransactionSystemException("commit outcome unknown"));
        var dto = new fu.mss301.movie.dto.MoviePosterUrlRequestDTO();
        dto.setUrl("https://example.com/poster.jpg");
        assertThrows(TransactionSystemException.class, () -> service.uploadPosterFromUrl(10, dto));
        verify(storage, never()).deleteImage(anyString());
    }

    @Test
    void cleanupFailureAfterSuccessfulCommitStillReturns200() throws Exception {
        doThrow(new IOException("cleanup failed")).when(storage).deleteImage("old-id");
        request().andExpect(status().isOk());
        verify(writer).replace(10, uploaded);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{"})
    void invalidJsonReturns400(String body) throws Exception {
        mvc.perform(put("/api/movies/10/poster/from-url").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(storage, writer);
    }

    @Test
    void fileUploadUsesTheSameReplacementFlow() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "poster.jpg", "image/jpeg", new byte[]{1});
        when(storage.uploadImage(any())).thenReturn(uploaded);
        mvc.perform(multipart("/api/movies/10/poster").file(file).with(request -> {
            request.setMethod("PUT");
            return request;
        })).andExpect(status().isOk()).andExpect(jsonPath("$.data.posterUrl").value(uploaded.secureUrl()));
        verify(writer).replace(10, uploaded);
        verify(storage).deleteImage("old-id");
    }

    private org.springframework.test.web.servlet.ResultActions request() throws Exception {
        return mvc.perform(put("/api/movies/10/poster/from-url").contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://example.com/poster.jpg\"}"));
    }
}
