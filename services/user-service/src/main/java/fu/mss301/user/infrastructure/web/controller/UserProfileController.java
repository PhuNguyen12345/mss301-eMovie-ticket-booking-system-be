package fu.mss301.user.infrastructure.web.controller;

import fu.mss301.common.dto.ApiResponse;
import fu.mss301.user.infrastructure.web.dto.ApiDtoMapper;
import fu.mss301.user.infrastructure.config.GatewayServiceAuthFilter;
import fu.mss301.user.infrastructure.web.dto.UserProfileResponse;
import fu.mss301.user.infrastructure.web.dto.UserSummaryResponse;
import fu.mss301.user.application.usecase.StatusCode;
import fu.mss301.user.application.usecase.UseCaseException;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.RequestParam;
import fu.mss301.user.application.port.in.UserProfileService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/users", "/api/v1/users"})
@SecurityRequirement(name = "bearerAuth")
public class UserProfileController {
    private final UserProfileService profiles;

    public UserProfileController(UserProfileService profiles) {
        this.profiles = profiles;
    }

    /** Staff lookup of customers by id, e.g. the owners of the orders on one page. ADMIN only. */
    @GetMapping(params = "ids")
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> summaries(
            @RequestAttribute(GatewayServiceAuthFilter.USER_ROLES_ATTRIBUTE) Set<String> roles,
            @RequestParam("ids") List<Integer> ids) {
        if (roles == null || !roles.contains("ADMIN")) {
            throw new UseCaseException(StatusCode.FORBIDDEN, "Access denied");
        }
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Users retrieved",
                profiles.summaries(ids).stream().map(ApiDtoMapper::toApi).toList()));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> me(
            @RequestAttribute(GatewayServiceAuthFilter.USER_ID_ATTRIBUTE) Integer userId) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Profile retrieved",
                ApiDtoMapper.toApi(profiles.me(userId))));
    }
}
