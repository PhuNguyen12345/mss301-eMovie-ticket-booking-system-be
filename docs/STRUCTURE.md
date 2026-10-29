# Cấu trúc hệ thống (Project Structure)

Dự án được thiết kế theo mô hình Multi-module Maven. Ở mức độ kiến trúc tổng thể, hệ thống tuân theo chuẩn **Microservices**. Ở mức độ chi tiết bên trong mỗi service, hệ thống áp dụng **Pragmatic DDD (DDD Tactical Design)**: logic nghiệp vụ nằm trong Aggregate (Rich Model) thay vì rải rác ở tầng Service như mô hình MVC 3 lớp trước đây. Hướng dẫn chi tiết và cách refactor: [PRAGMATIC_DDD.md](PRAGMATIC_DDD.md).

## 1. Cấu trúc thư mục toàn cục

Sơ đồ dưới đây thể hiện kiến trúc tổng thể của toàn bộ hệ thống, bao gồm thư mục dùng chung (`common`) và cấu trúc mẫu bên trong một microservice tiêu biểu (`booking-service`).

```text
e-movie-ticket-booking-system/
├── api-gateway/            # Cổng định tuyến API & Trạm gác bảo mật (Xác thực JWT, gửi X-User-Id/X-User-Roles)
├── discovery-server/       # Máy chủ đăng ký dịch vụ (Netflix Eureka)
│
├── common/                 # Module chứa code dùng chung cho toàn bộ hệ thống
│   └── src/main/java/fu/mss301/common/
│       ├── dto/
│       │   ├── ApiResponse.java            # Format chuẩn (status, message, data, timestamp) cho mọi API
│       │   └── PageResponseDTO.java        # Format phân trang chuẩn
│       └── exception/
│           ├── ApiException.java           # Exception mang HTTP status
│           └── GlobalExceptionHandler.java # Bắt lỗi toàn cục (@RestControllerAdvice)
│
├── services/               # Thư mục chứa các Microservices nghiệp vụ
│   ├── booking-service/    # Giữ ghế, đặt vé, thanh toán PayOS, hoàn tiền (service mẫu Pragmatic DDD)
│   │   └── src/main/java/fu/mss301/booking/
│   │       ├── BookingServiceApplication.java
│   │       ├── domain/            # Aggregate (Booking, PaymentTransaction), Entity con, Value Object,
│   │       │   │                  # Domain Event, Repository interface – KHÔNG phụ thuộc Web/Feign/SDK
│   │       │   ├── shared/        # DomainException, InvalidStateTransitionException
│   │       │   ├── booking/       # Booking, BookingSeat, BookingCode, SeatSelection, BookingRepository, event/
│   │       │   └── payment/       # PaymentTransaction, Refund, PaymentRepository, event/
│   │       ├── application/       # Use case điều phối (1 transaction = 1 aggregate), View trả về
│   │       │   ├── port/          # Interface tới hệ thống ngoài: ShowtimeCatalog, SeatLayoutProvider, PaymentGateway
│   │       │   ├── booking/       # HoldSeatsUseCase, CancelBookingUseCase, SeatMapQuery, ...
│   │       │   ├── payment/       # InitiatePaymentUseCase, HandlePayosWebhookUseCase, ...
│   │       │   └── policy/        # Lắng nghe Domain Event → cập nhật aggregate khác
│   │       ├── infrastructure/    # Chi tiết kỹ thuật implement domain/port
│   │       │   ├── persistence/   # Spring Data JPA + adapter của Repository
│   │       │   ├── client/        # OpenFeign gọi showtime/cinema + adapter
│   │       │   ├── payos/         # PayOS SDK adapter
│   │       │   ├── config/        # @ConfigurationProperties, Clock, bean cấu hình
│   │       │   └── scheduling/    # Job hết hạn giữ ghế & đối soát thanh toán
│   │       └── presentation/      # REST API
│   │           ├── controller/
│   │           ├── dto/           # Request body + Bean Validation
│   │           ├── security/      # Đọc danh tính người dùng từ header của gateway
│   │           └── exception/     # Map exception → HTTP status
│   │
│   ├── cinema-service/     # Quản lý rạp, phòng chiếu, sơ đồ ghế vật lý
│   ├── movie-service/      # Quản lý phim, danh mục
│   ├── showtime-service/   # Quản lý lịch chiếu, chống xung đột thời gian
│   └── user-service/       # Quản lý người dùng, cấp phát JWT, lịch sử
│
├── docs/                   # Tài liệu thiết kế, API Contracts, Sơ đồ kiến trúc
├── tests/smoke/            # Smoke test qua gateway (compose.test.yml)
├── compose.yml             # Chạy hệ thống bằng Docker Compose
├── pom.xml                 # Maven Parent POM quản lý dependencies chung
└── README.md               # Tổng quan dự án

```

## 2. Quy chuẩn thiết kế (Coding Convention)

* **Tách biệt ranh giới (Domain Isolation):** Mỗi microservice sở hữu một cơ sở dữ liệu độc lập. Giao tiếp liên dịch vụ (Inter-service communication) bắt buộc thực hiện thông qua HTTP/OpenFeign; trong service theo DDD, Feign client nằm ở `infrastructure/client` và được che sau một `port` ở `application`.
* **Rich Domain Model:** Entity không có public setter; tạo bằng static factory; thay đổi trạng thái qua method có tên nghiệp vụ (`confirm()`, `expire()`, `cancelBy()`); invariant được kiểm tra ngay trong Aggregate.
* **1 transaction = 1 aggregate:** Một transaction chỉ cập nhật một Aggregate. Cần cập nhật Aggregate khác thì phát Domain Event và xử lý ở `application/policy` trong transaction riêng. Gọi service khác / bên thứ ba luôn nằm ngoài transaction DB.
* **Không bypass Aggregate Root:** Không tạo repository cho entity con; thay đổi entity con thông qua root rồi lưu bằng repository của root.
* **Đồng bộ dữ liệu trả về:** Mọi API Endpoints từ tất cả các services đều phải bọc dữ liệu trả về bằng class `ApiResponse` từ module `common` để đảm bảo tính nhất quán cho phía Frontend.
* **Xử lý lỗi tập trung:** Domain chỉ ném `DomainException` (không biết HTTP). Tầng `presentation/exception` map lỗi nghiệp vụ sang HTTP status; các lỗi còn lại (`ApiException`, validation, 500) do `GlobalExceptionHandler` của `common` xử lý.
* **Quản lý tiến trình ngầm:** Các tác vụ không thuộc luồng giao dịch chính (gửi email, hết hạn giữ ghế, đối soát thanh toán) chạy bất đồng bộ / theo lịch và chỉ gọi use case, không chứa logic nghiệp vụ.

Checklist review đầy đủ: [PRAGMATIC_DDD.md – mục 4](PRAGMATIC_DDD.md#4-quy-tắc-vàng-checklist-khi-review-pr).

## 3. Trạng thái kiến trúc từng service

| Service | Kiến trúc hiện tại | Ghi chú |
| --- | --- | --- |
| booking-service | Pragmatic DDD | Service mẫu – xem [BOOKING_API.md](BOOKING_API.md) |
| user-service | Hexagonal (DDD thuần, domain tách JPA) | Tương thích, không bắt buộc đổi |
| movie-service | MVC 3 lớp | Refactor dần theo [PRAGMATIC_DDD.md – mục 6](PRAGMATIC_DDD.md#6-hướng-dẫn-refactor-service-mvc-sang-pragmatic-ddd) |
| showtime-service | MVC 3 lớp | Refactor dần; ví dụ `Showtime` đã có trong mục 6.3 |
| cinema-service | Chưa triển khai | Nên viết thẳng theo Pragmatic DDD; cần cung cấp `GET /api/screens/{screenId}/seats` cho booking |
