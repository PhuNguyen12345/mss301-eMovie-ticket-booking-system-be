package fu.mss301.movie.dto;

public record ImageUploadResult(
    String secureUrl, 
    String publicId
) {}
