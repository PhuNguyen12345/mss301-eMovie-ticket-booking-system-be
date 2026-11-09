package fu.mss301.booking.presentation.controller;

import fu.mss301.booking.application.voucher.MyVoucherView;
import fu.mss301.booking.application.voucher.VoucherService;
import fu.mss301.booking.application.voucher.VoucherView;
import fu.mss301.booking.presentation.dto.AssignVoucherRequest;
import fu.mss301.booking.presentation.dto.ClaimVoucherRequest;
import fu.mss301.booking.presentation.dto.VoucherRequest;
import fu.mss301.booking.presentation.security.AuthenticatedUser;
import fu.mss301.booking.presentation.security.CurrentUser;
import fu.mss301.common.dto.ApiResponse;
import fu.mss301.common.dto.PageResponseDTO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/vouchers")
public class VoucherController {
    private final VoucherService vouchers;

    public VoucherController(VoucherService vouchers) {
        this.vouchers = vouchers;
    }

    @GetMapping("/me")
    public ApiResponse<List<MyVoucherView>> mine(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.success(vouchers.mine(user.id()), "Vouchers retrieved successfully");
    }

    @PostMapping("/claim")
    public ApiResponse<MyVoucherView> claim(@CurrentUser AuthenticatedUser user,
                                            @Valid @RequestBody ClaimVoucherRequest request) {
        return ApiResponse.success(vouchers.claim(user.id(), request.code()), "Voucher claimed successfully");
    }

    @GetMapping
    public ApiResponse<PageResponseDTO<VoucherView>> list(@CurrentUser AuthenticatedUser user,
                                                          @RequestParam(name = "page", defaultValue = "0") int page,
                                                          @RequestParam(name = "size", defaultValue = "10") int size) {
        requireAdmin(user);
        var result = vouchers.list(page, size);
        return ApiResponse.success(new PageResponseDTO<>(result.content(), result.page(), result.size(),
            result.totalElements(), result.totalPages(), result.first(), result.last()), "Vouchers retrieved successfully");
    }

    @GetMapping("/{id}")
    public ApiResponse<VoucherView> get(@CurrentUser AuthenticatedUser user, @PathVariable("id") Integer id) {
        requireAdmin(user);
        return ApiResponse.success(vouchers.get(id), "Voucher retrieved successfully");
    }

    @PostMapping
    public ResponseEntity<ApiResponse<VoucherView>> create(@CurrentUser AuthenticatedUser user,
                                                           @Valid @RequestBody VoucherRequest request) {
        requireAdmin(user);
        var result = vouchers.create(request.toCommand());
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(result.id()).toUri();
        return ResponseEntity.created(location).body(new ApiResponse<>(201, "Voucher created successfully", result));
    }

    @PutMapping("/{id}")
    public ApiResponse<VoucherView> update(@CurrentUser AuthenticatedUser user, @PathVariable("id") Integer id,
                                           @Valid @RequestBody VoucherRequest request) {
        requireAdmin(user);
        return ApiResponse.success(vouchers.update(id, request.toCommand()), "Voucher updated successfully");
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<VoucherView> activate(@CurrentUser AuthenticatedUser user, @PathVariable("id") Integer id) {
        requireAdmin(user);
        return ApiResponse.success(vouchers.activate(id), "Voucher activated successfully");
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<VoucherView> deactivate(@CurrentUser AuthenticatedUser user, @PathVariable("id") Integer id) {
        requireAdmin(user);
        return ApiResponse.success(vouchers.deactivate(id), "Voucher deactivated successfully");
    }

    @PostMapping("/{id}/assign")
    public ApiResponse<VoucherView> assign(@CurrentUser AuthenticatedUser user, @PathVariable("id") Integer id,
                                           @Valid @RequestBody AssignVoucherRequest request) {
        requireAdmin(user);
        return ApiResponse.success(vouchers.assign(id, request.userId()), "Voucher assigned successfully");
    }

    private static void requireAdmin(AuthenticatedUser user) {
        if (!user.isAdmin()) throw new ApiException(HttpStatus.FORBIDDEN, "Access denied");
    }
}
