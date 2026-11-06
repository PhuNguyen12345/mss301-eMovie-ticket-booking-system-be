package fu.mss301.cinema.infrastructure.web.dto;

import fu.mss301.cinema.domain.model.FacilityStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public final class CinemaRequests {
    private CinemaRequests() {}

    @Schema(description = "Thông tin tạo hoặc cập nhật rạp")
    public record CinemaBody(
        @Schema(description = "Mã rạp duy nhất, viết hoa; 1–32 ký tự A–Z, 0–9, _ hoặc -", example = "DEMO_HN")
        @NotBlank @Pattern(regexp = "[A-Z0-9_-]{1,32}") String code,
        @Schema(description = "Tên rạp", example = "Rạp Hà Nội")
        @NotBlank @Size(max = 150) String name,
        @Schema(description = "Địa điểm dùng để tìm suất chiếu", example = "Hà Nội")
        @NotBlank @Size(max = 255) String location) {}
    @Schema(description = "Thông tin tạo hoặc cập nhật phòng chiếu")
    public record ScreenBody(
        @Schema(description = "Mã phòng duy nhất trong một rạp", example = "S1")
        @NotBlank @Pattern(regexp = "[A-Z0-9_-]{1,32}") String code,
        @Schema(description = "Tên phòng chiếu", example = "Phòng chiếu 1")
        @NotBlank @Size(max = 100) String name) {}
    @Schema(description = "Thông tin tạo hoặc cập nhật loại ghế")
    public record SeatTypeBody(
        @Schema(description = "Mã loại ghế duy nhất", example = "VIP")
        @NotBlank @Pattern(regexp = "[A-Z0-9_-]{1,32}") String code,
        @Schema(description = "Tên loại ghế", example = "Ghế VIP")
        @NotBlank @Size(max = 100) String name) {}
    @Schema(description = "Thông tin tạo ghế vật lý; vị trí và loại ghế cố định sau khi tạo")
    public record SeatBody(
        @Schema(description = "Mã hàng viết hoa, tối đa 8 ký tự chữ hoặc số", example = "A")
        @NotBlank @Pattern(regexp = "[A-Z0-9]{1,8}") String rowCode,
        @Schema(description = "Số ghế trong hàng, bắt đầu từ 1", example = "1")
        @NotNull @Positive Integer seatNumber,
        @Schema(description = "ID loại ghế đã tồn tại", example = "2")
        @NotNull @Positive Integer seatTypeId) {}
    @Schema(description = "Trạng thái mới; rạp chỉ nhận ACTIVE hoặc INACTIVE")
    public record StatusBody(
        @Schema(description = "ACTIVE, INACTIVE hoặc MAINTENANCE (chỉ phòng và ghế)", example = "INACTIVE")
        @NotNull FacilityStatus status) {}
}
