package fu.mss301.movie.service;

import java.io.IOException;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import fu.mss301.movie.dto.ImageUploadResult;
import fu.mss301.movie.dto.MoviePosterResponseDTO;
import fu.mss301.movie.exception.MovieNotFoundException;
import fu.mss301.movie.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

//dieu phoi upload, luu db va don anh cu 
@Service 
@RequiredArgsConstructor 
@Slf4j 
public class MoviePosterService {
    private final MovieRepository movieRepository; 
    private final CloudinaryService cloudinaryService; 
    private final MoviePosterWriter moviePosterWriter; 

    //ham dieu phoi upload, khong danh dau transactional 
    public MoviePosterResponseDTO uploadPoster(
        Integer movieId, 
        MultipartFile file
    ) throws IOException {
        //check truong hop id phim duoc gan vao khong hop le 
        if (movieId == null || movieId <= 0) {
            throw new IllegalArgumentException(
                "Movie Id must be positive"
            ); 
        }

        //check truong hop id khong ton tai 
        if (!movieRepository.existsById(movieId)) {
            throw new MovieNotFoundException(movieId);
        }

        //uploadImage va lay result 
        ImageUploadResult uploaded = cloudinaryService.uploadImage(file); 
        //khai bao 1 ham replacement, su dung khi replace thanh cong
        MoviePosterWriter.PosterReplacement replacement; 

        //try-catch de replace trong truong hop ma da co anh tu truoc 
        try {
            replacement = moviePosterWriter.replace(movieId, uploaded); 
        } catch (RuntimeException ex) {
            //voi rollback da co, xoa anh moi chua su dung 
            deleteQuietly(uploaded.publicId()); 
            throw ex; 
        }

        //xoa anh chua public id cu 
        deleteQuietly(replacement.oldPublicId()); 
        return replacement.response(); 
    }

    //implement ham delete image 
    private void deleteQuietly(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return; 
        }
        try {
            cloudinaryService.deleteImage(publicId);
        } catch (Exception ex) {
            log.warn("Could not clean up image {}", publicId, ex);
        }
    }

}
