package fu.mss301.booking.infrastructure.web.controller;

import fu.mss301.booking.application.combo.ComboService;
import fu.mss301.booking.application.combo.ComboView;
import fu.mss301.booking.infrastructure.web.dto.ComboRequest;
import fu.mss301.booking.infrastructure.web.security.AuthenticatedUser;
import fu.mss301.booking.infrastructure.web.security.CurrentUser;
import fu.mss301.common.dto.ApiResponse;
import fu.mss301.common.exception.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/combos")
public class ComboController {
    private final ComboService combos;

    public ComboController(ComboService combos) {
        this.combos = combos;
    }

    @GetMapping
    public ApiResponse<List<ComboView>> listOnSale() {
        return ApiResponse.success(combos.listOnSale(), "Combos retrieved successfully");
    }

    @GetMapping("/all")
    public ApiResponse<List<ComboView>> listAll(@CurrentUser AuthenticatedUser user) {
        requireAdmin(user);
        return ApiResponse.success(combos.listAll(), "Combos retrieved successfully");
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ComboView>> create(@CurrentUser AuthenticatedUser user,
                                                         @Valid @RequestBody ComboRequest request) {
        requireAdmin(user);
        var result = combos.create(request.toCommand());
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(result.id()).toUri();
        return ResponseEntity.created(location).body(new ApiResponse<>(201, "Combo created successfully", result));
    }

    @PutMapping("/{id}")
    public ApiResponse<ComboView> update(@CurrentUser AuthenticatedUser user, @PathVariable("id") Integer id,
                                         @Valid @RequestBody ComboRequest request) {
        requireAdmin(user);
        return ApiResponse.success(combos.update(id, request.toCommand()), "Combo updated successfully");
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<ComboView> activate(@CurrentUser AuthenticatedUser user, @PathVariable("id") Integer id) {
        requireAdmin(user);
        return ApiResponse.success(combos.activate(id), "Combo activated successfully");
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<ComboView> deactivate(@CurrentUser AuthenticatedUser user, @PathVariable("id") Integer id) {
        requireAdmin(user);
        return ApiResponse.success(combos.deactivate(id), "Combo deactivated successfully");
    }

    private static void requireAdmin(AuthenticatedUser user) {
        if (!user.isAdmin()) throw new ApiException(HttpStatus.FORBIDDEN, "Access denied");
    }
}
