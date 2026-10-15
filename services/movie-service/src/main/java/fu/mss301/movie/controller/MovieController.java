package fu.mss301.movie.controller;

import fu.mss301.common.dto.ApiResponse;
import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.movie.dto.MoviePosterResponseDTO;
import fu.mss301.movie.dto.MovieRequestDTO;
import fu.mss301.movie.dto.MovieResponseDTO;
import fu.mss301.movie.dto.MovieDetailResponseDTO;
import fu.mss301.movie.dto.MoviePosterUrlRequestDTO;
import fu.mss301.movie.dto.MovieStatusRequestDTO;
import fu.mss301.movie.enums.MovieStatus;
import fu.mss301.movie.service.MoviePosterService;
import fu.mss301.movie.service.MovieService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.PathVariable;


@RestController
@RequestMapping("/api/movies")
public class MovieController {

    private final MovieService movieService;
    private final MoviePosterService moviePosterService; 

    public MovieController(
        MovieService movieService, 
        MoviePosterService moviePosterService
    ) {
        this.movieService = movieService;
        this.moviePosterService = moviePosterService; 
    }

    @GetMapping
    public ApiResponse<PageResponseDTO<MovieResponseDTO>> getMovies(
        @RequestParam(name = "keyword", required = false) String keyword,
        @RequestParam(name = "status", required = false) MovieStatus status,
        @RequestParam(name = "genreId", required = false) Integer genreId,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        return ApiResponse.success(
            movieService.getMovies(keyword, status, genreId, page, size),
            "Movies retrieved successfully"
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<MovieDetailResponseDTO> getMovieById(@PathVariable("id") Integer id) {
        return ApiResponse.success(movieService.getMovieById(id), "Movie retrieved successfully");
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MovieResponseDTO>> createMovie(
        @RequestBody MovieRequestDTO request
    ) {
        MovieResponseDTO movie = movieService.createMovie(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(movie.getId()).toUri();
        return ResponseEntity.created(location).body(
            new ApiResponse<>(201, "Movie created successfully", movie)
        );
    }

    @PutMapping("/{id}")
    public ApiResponse<MovieResponseDTO> updateMovie(
        @PathVariable("id") Integer id,
        @RequestBody MovieRequestDTO request
    ) {
        return ApiResponse.success(movieService.updateMovie(id, request), "Movie updated successfully");
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<MovieResponseDTO> updateMovieStatus(
        @PathVariable("id") Integer id,
        @RequestBody MovieStatusRequestDTO request
    ) {
        return ApiResponse.success(
            movieService.updateMovieStatus(id, request.status()),
            "Movie status updated successfully"
        );
    }

    @PostMapping("/{id}/expire")
    public ApiResponse<Void> expireMovie(@PathVariable("id") Integer id) {
        movieService.expireMovie(id);
        return ApiResponse.success(null, "Movie status changed to EXPIRED");
    }

    @PutMapping(value = "/{id}/poster/from-url", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<MoviePosterResponseDTO> uploadPosterFromUrl(
        @PathVariable("id") Integer id,
        @RequestBody MoviePosterUrlRequestDTO request
    ) throws IOException {
        return ApiResponse.success(
            moviePosterService.uploadPosterFromUrl(id, request),
            "Poster updated successfully"
        );
    }

    //api cho phep upload poster 
    @PutMapping(
        value = "/{id}/poster", 
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResponse<MoviePosterResponseDTO> uploadPoster(
        @PathVariable("id") Integer id, 
        @RequestParam("file") MultipartFile file
    ) throws IOException {        
        return ApiResponse.success(
            moviePosterService.uploadPoster(id, file), 
            "Poster updated successfully"
        );
    }
}
