package fu.mss301.movie.controller;

import fu.mss301.common.dto.ApiResponse;
import fu.mss301.movie.dto.*;
import fu.mss301.movie.service.MovieRelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/movies/{movieId}")
@RequiredArgsConstructor
public class MovieRelationController {
    private final MovieRelationService relationService;

    @GetMapping("/genres")
    public ApiResponse<List<GenreResponseDTO>> getGenres(@PathVariable("movieId") Integer movieId) {
        return ApiResponse.success(relationService.getGenres(movieId), "Movie genres retrieved successfully");
    }

    @PutMapping("/genres")
    public ApiResponse<List<GenreResponseDTO>> replaceGenres(
        @PathVariable("movieId") Integer movieId, @RequestBody MovieGenresRequestDTO request
    ) {
        return ApiResponse.success(relationService.replaceGenres(movieId, request), "Movie genres updated successfully");
    }

    @GetMapping("/credits")
    public ApiResponse<List<MovieCreditResponseDTO>> getCredits(@PathVariable("movieId") Integer movieId) {
        return ApiResponse.success(relationService.getCredits(movieId), "Movie credits retrieved successfully");
    }

    @PutMapping("/credits")
    public ApiResponse<List<MovieCreditResponseDTO>> replaceCredits(
        @PathVariable("movieId") Integer movieId, @RequestBody MovieCreditsRequestDTO request
    ) {
        return ApiResponse.success(relationService.replaceCredits(movieId, request), "Movie credits updated successfully");
    }
}
