package fu.mss301.movie.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fu.mss301.movie.dto.ImageUploadResult;
import fu.mss301.movie.dto.MoviePosterResponseDTO;
import fu.mss301.movie.entity.Movie;
import fu.mss301.movie.exception.MovieNotFoundException;
import fu.mss301.movie.repository.MovieRepository;
import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 
public class MoviePosterWriter {
    private final MovieRepository movieRepository; 

    //ham thay the hinh anh 
    @Transactional 
    public PosterReplacement replace(
        Integer movieId, 
        ImageUploadResult uploaded 
    ) {
        Movie movie = movieRepository.findByIdForPosterUpdate(movieId) 
            .orElseThrow(() -> new MovieNotFoundException(movieId)); 

        String oldPublicId = movie.getPosterPublicId(); 
        
        movie.setPosterUrl(uploaded.secureUrl());
        movie.setPosterPublicId(uploaded.publicId());

        //Entity quan ly boi Hibernate, dirty checking truoc khi commit 
        return new PosterReplacement(
            new MoviePosterResponseDTO(
                movieId, 
                uploaded.secureUrl()
            ), oldPublicId
        ); 
    }

    public record PosterReplacement(
        MoviePosterResponseDTO response, 
        String oldPublicId 
    ) {}
}
