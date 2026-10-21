package fu.mss301.user.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @Pattern(regexp = "^$|^\\+?[0-9]{9,15}$", message = "must contain 9 to 15 digits, optionally prefixed with +")
        String phone,
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
