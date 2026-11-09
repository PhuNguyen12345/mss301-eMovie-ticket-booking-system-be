package fu.mss301.movie.application.dto;

public record ImageUploadResult(
    String secureUrl, 
    String publicId
) {}
