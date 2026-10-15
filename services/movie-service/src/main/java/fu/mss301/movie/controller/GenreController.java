package fu.mss301.movie.controller;

import fu.mss301.common.dto.ApiResponse;
import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.movie.dto.GenreRequestDTO;
import fu.mss301.movie.dto.GenreResponseDTO;
import fu.mss301.movie.service.GenreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/genres")
@RequiredArgsConstructor
public class GenreController {
    private final GenreService genreService;

    @GetMapping
    public ApiResponse<PageResponseDTO<GenreResponseDTO>> getGenres(
        @RequestParam(name = "keyword", required = false) String keyword,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        return ApiResponse.success(genreService.getGenres(keyword, page, size), "Genres retrieved successfully");
    }

    @GetMapping("/{id}")
    public ApiResponse<GenreResponseDTO> getGenreById(@PathVariable("id") Integer id) {
        return ApiResponse.success(genreService.getGenreById(id), "Genre retrieved successfully");
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GenreResponseDTO>> createGenre(@RequestBody GenreRequestDTO request) {
        GenreResponseDTO genre = genreService.createGenre(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(genre.getId()).toUri();
        return ResponseEntity.created(location).body(new ApiResponse<>(201, "Genre created successfully", genre));
    }

    @PutMapping("/{id}")
    public ApiResponse<GenreResponseDTO> updateGenre(
        @PathVariable("id") Integer id, @RequestBody GenreRequestDTO request
    ) {
        return ApiResponse.success(genreService.updateGenre(id, request), "Genre updated successfully");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteGenre(@PathVariable("id") Integer id) {
        genreService.deleteGenre(id);
        return ApiResponse.success(null, "Genre deleted successfully");
    }
}
